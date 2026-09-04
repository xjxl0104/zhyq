package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "能耗管理-表计")
@RestController
@RequestMapping("/energy/meter")
@RequiredArgsConstructor
public class MeterController {

    private final MeterMapper meterMapper;
    // 表计展示要跨 building / contract / tenant / energy 四个模块取数,按 DashboardController
    // 的既有做法用 JdbcTemplate 只读聚合,不为了几个展示字段拉一堆跨包 mapper 依赖
    private final JdbcTemplate jdbc;
    private final BillMapper billMapper;

    /** 能源费账单的费用类型,与账单页 feeTypes 里的取值保持一致 */
    private static final String FEE_TYPE_ENERGY = "能源费";

    @Operation(summary = "分页查询表计(含租户、最近抄表与本期用量)")
    @GetMapping("/page")
    public Result<PageResult<Meter>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String code,
                                          @RequestParam(required = false) String energyType,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Long tenantRefId,
                                          @RequestParam(required = false) String meterRole,
                                          // 账期 yyyy-MM:给了就看那个月的抄表结果,不给看最近一次
                                          @RequestParam(required = false) String period) {
        LambdaQueryWrapper<Meter> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Meter::getCode, code)
          .eq(StringUtils.hasText(energyType), Meter::getEnergyType, energyType)
          .eq(status != null, Meter::getStatus, status)
          .eq(StringUtils.hasText(meterRole), Meter::getMeterRole, meterRole)
          // 租户不是表计自己的字段(表计只挂房间),按租户筛要经 房间→合同 绕一层,
          // 故用子查询而不是给 eng_meter 冗余一列 —— 冗余列会和换租后不同步
          .inSql(tenantRefId != null, Meter::getRoomId,
                  "SELECT cr.room_id FROM biz_contract_room cr "
                  + "JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status = 5 "
                  + "WHERE cr.deleted = 0 AND c.tenant_ref_id = " + tenantRefId)
          .orderByDesc(Meter::getId);
        IPage<Meter> p = meterMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        enrich(p.getRecords(), period);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "表计详情")
    @GetMapping("/{id}")
    public Result<Meter> get(@PathVariable Long id) {
        Meter meter = meterMapper.selectById(id);
        if (meter != null) {
            enrich(List.of(meter), null);
        }
        return Result.ok(meter);
    }

    @Operation(summary = "新增表计")
    @PostMapping
    @OperationLog(module = "智能表计", action = "新增")
    public Result<Long> add(@RequestBody Meter meter) {
        meterMapper.insert(meter);
        return Result.ok(meter.getId());
    }

    @Operation(summary = "修改表计")
    @PutMapping
    @OperationLog(module = "智能表计", action = "修改")
    public Result<Void> update(@RequestBody Meter meter) {
        meterMapper.updateById(meter);
        return Result.ok();
    }

    @Operation(summary = "删除表计")
    @DeleteMapping("/{id}")
    @OperationLog(module = "智能表计", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        meterMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 按最近一次抄表生成能源费账单。
     *
     * <p>抄表记录本身已经算好了 fee,这里只做「把它变成一张可收款的账单」:
     * 账期取上次抄表日 ~ 本次抄表日,租户取房间对应的执行中合同。</p>
     *
     * <p>幂等靠 fin_bill 的唯一索引 uk_bill_billing_key:一次抄表最多一张账单,
     * 重复点击会被库直接挡掉而不是出两张。</p>
     */
    @Operation(summary = "按最近一次抄表生成能源费账单(一次抄表只出一张,幂等)")
    @PostMapping("/{id}/bill")
    @OperationLog(module = "智能表计", action = "生成能源费账单")
    public Result<Bill> createBill(@PathVariable Long id) {
        Meter meter = meterMapper.selectById(id);
        if (meter == null) {
            throw new BizException("表计不存在: " + id);
        }
        enrich(List.of(meter), null);
        if (meter.getLatestReadingId() == null) {
            throw new BizException("该表计还没有抄表记录,先抄表再计费");
        }
        if (meter.getTenantRefId() == null) {
            throw new BizException("该表计所在房间没有执行中的合同,找不到该向谁收费;请先在合同里关联房源");
        }
        BigDecimal fee = meter.getLatestFee();
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("最近一次抄表的金额为 0,没有可出账的费用");
        }

        String key = billingKey(id, meter.getLatestReadingId());
        Bill existed = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getBillingKey, key).last("limit 1"));
        if (existed != null) {
            return Result.ok(existed);
        }

        LocalDate end = meter.getPeriodEnd() == null ? LocalDate.now() : meter.getPeriodEnd();
        Bill bill = new Bill();
        bill.setCode("EN" + meter.getCode() + end.format(DateTimeFormatter.ofPattern("yyyyMM")));
        // billing_active_key 是生成列(deleted=0 时等于 billing_key),唯一索引挂在它上面,
        // 这里只设 billing_key,删除后自动让出该键
        bill.setBillingKey(key);
        bill.setTenantRefId(meter.getTenantRefId());
        bill.setRoomId(meter.getRoomId());
        bill.setProjectId(meter.getProjectId());
        bill.setBuildingId(meter.getBuildingId());
        bill.setDirection(1);
        bill.setFeeType(FEE_TYPE_ENERGY);
        bill.setSource("抄表");
        bill.setStatus(3);
        bill.setAmount(fee);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setLateFee(BigDecimal.ZERO);
        bill.setPeriodStart(meter.getPeriodStart() == null ? end : meter.getPeriodStart());
        bill.setPeriodEnd(end);
        bill.setDueDate(end.plusMonths(1));
        bill.setInvoiceStatus(0);
        bill.setRemark("表计 %s(%s) %s 用量 %s".formatted(
                meter.getCode(), meter.getEnergyType(), end,
                meter.getUsageAmount() == null ? "-" : meter.getUsageAmount().toPlainString()));
        try {
            billMapper.insert(bill);
        } catch (DuplicateKeyException e) {
            // 并发下另一次点击先落库,回查返回,保持幂等
            Bill dup = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getBillingKey, key).last("limit 1"));
            if (dup != null) {
                return Result.ok(dup);
            }
            throw e;
        }
        return Result.ok(bill);
    }

    /**
     * 某个表计的操作日志。
     *
     * <p>sys_oper_log 没有业务对象 id 列,只能按 module + 请求路径/参数里出现的表计编号匹配。
     * 用编号而不是 id:编号是唯一字符串,不会像 "3" 那样命中别的数字。</p>
     */
    @Operation(summary = "某个表计的操作日志")
    @GetMapping("/{id}/oper-logs")
    public Result<List<Map<String, Object>>> operLogs(@PathVariable Long id) {
        Meter meter = meterMapper.selectById(id);
        if (meter == null) {
            throw new BizException("表计不存在: " + id);
        }
        String code = meter.getCode() == null ? "" : meter.getCode();
        return Result.ok(jdbc.queryForList(
                "SELECT id, module, action, http_method, operator, ip, success, error_msg, cost_ms, create_time "
                        + "FROM sys_oper_log WHERE module = ? AND (req_uri LIKE ? OR params LIKE ?) "
                        + "ORDER BY id DESC LIMIT 100",
                "智能表计", "%/energy/meter/" + id + "%", "%" + code + "%"));
    }

    @Operation(summary = "全部在用表计(下拉)")
    @GetMapping("/list")
    public Result<List<Meter>> list() {
        return Result.ok(meterMapper.selectList(
                new LambdaQueryWrapper<Meter>().eq(Meter::getStatus, 1).orderByDesc(Meter::getId)));
    }

    @Operation(summary = "按能源类型统计表计数量")
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        List<Meter> all = meterMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (Meter m : all) {
            String type = StringUtils.hasText(m.getEnergyType()) ? m.getEnergyType() : "未分类";
            grouped.merge(type, 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("energyType", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.ok(result);
    }

    /**
     * 给表计补上「谁在用、抄到哪天、这期用了多少」。
     *
     * <p>表计表原来只有编号/名称/倍率/上次读数,这几列答不了日常最要紧的问题。
     * 租户经 房间 → 合同房源 → 合同 → 租客 反查,只认未删除且执行中(status=5)的合同 ——
     * 已退租的旧合同不该把表计算在别人头上。抄表数据取该表计最近一次读数,
     * 账期取「上次抄表日 ~ 本次抄表日」。</p>
     *
     * <p>逐条查会 N+1,这里按整页 id 批量查三次(租户 / 最近抄表 / 上次抄表日)。</p>
     */
    private void enrich(List<Meter> meters, String period) {
        if (meters == null || meters.isEmpty()) {
            return;
        }
        // id 来自库里的主键,拼进 SQL 无注入面;用 IN 列表避免逐条查
        String idList = meters.stream().map(m -> String.valueOf(m.getId()))
                .collect(Collectors.joining(","));

        // 房间号:与租户分开查。一个房间常挂着多条合同房源记录(历史合同 + 当前合同),
        // 混在一条 LEFT JOIN 里取「第一条」会取到已退租/已删除那条,租户就成了空
        Map<Long, String> roomByMeter = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT m.id AS meter_id, r.code AS room_code FROM eng_meter m "
                        + "LEFT JOIN biz_room r ON r.id = m.room_id AND r.deleted = 0 "
                        + "WHERE m.id IN (" + idList + ")")) {
            roomByMeter.put(asLong(row.get("meter_id")), (String) row.get("room_code"));
        }

        // 在租租户:全用 INNER JOIN,只有「未删除 + 执行中(status=5)」的合同才会出现;
        // 同一房间有多份在执行的合同时按起租日取最新那份(putIfAbsent 保留首行)
        Map<Long, Map<String, Object>> tenantByMeter = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT m.id AS meter_id, t.id AS tenant_ref_id, t.name AS tenant_name "
                        + "FROM eng_meter m "
                        + "JOIN biz_contract_room cr ON cr.room_id = m.room_id AND cr.deleted = 0 "
                        + "JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status = 5 "
                        + "JOIN biz_tenant t ON t.id = c.tenant_ref_id AND t.deleted = 0 "
                        + "WHERE m.id IN (" + idList + ") "
                        + "ORDER BY c.start_date DESC, c.id DESC")) {
            tenantByMeter.putIfAbsent(asLong(row.get("meter_id")), row);
        }

        // 抄表数据:不给账期看最近一次;给了账期就只看那个月(按月查水电多少用这条)。
        // 同一时刻并列时取 id 最大的那条,结果稳定不随机
        boolean byPeriod = StringUtils.hasText(period);
        String periodFilter = byPeriod ? " AND DATE_FORMAT(o.read_time, '%Y-%m') = ? " : "";
        String periodFilterInner = byPeriod ? " AND DATE_FORMAT(x.read_time, '%Y-%m') = ? " : "";
        Object[] args = byPeriod ? new Object[]{period, period} : new Object[0];
        Map<Long, Map<String, Object>> readingByMeter = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT e.meter_id, e.id AS reading_id, e.curr_reading, e.usage_amount, e.fee, e.read_time "
                        + "FROM eng_reading e JOIN ("
                        + "  SELECT o.meter_id, MAX(o.id) AS max_id FROM eng_reading o"
                        + "  WHERE o.deleted = 0 AND o.meter_id IN (" + idList + ")"
                        + periodFilter
                        + "    AND o.read_time = (SELECT MAX(x.read_time) FROM eng_reading x"
                        + "                       WHERE x.deleted = 0 AND x.meter_id = o.meter_id"
                        + periodFilterInner + ")"
                        + "  GROUP BY o.meter_id) latest ON latest.max_id = e.id", args)) {
            readingByMeter.put(asLong(row.get("meter_id")), row);
        }

        Map<Long, LocalDate> prevReadDate = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT e.meter_id, MAX(e.read_time) AS prev_time FROM eng_reading e "
                        + "WHERE e.deleted = 0 AND e.meter_id IN (" + idList + ")"
                        + "  AND e.read_time < (SELECT MAX(x.read_time) FROM eng_reading x"
                        + "                     WHERE x.deleted = 0 AND x.meter_id = e.meter_id) "
                        + "GROUP BY e.meter_id")) {
            LocalDateTime t = toDateTime(row.get("prev_time"));
            if (t != null) {
                prevReadDate.put(asLong(row.get("meter_id")), t.toLocalDate());
            }
        }

        // 哪几次抄表已经出过账单:前端据此把「计费」按钮变成「已出账」,避免重复点
        List<String> keys = readingByMeter.entrySet().stream()
                .map(e -> billingKey(e.getKey(), asLong(e.getValue().get("reading_id"))))
                .toList();
        Set<String> billedKeys = keys.isEmpty() ? Set.of()
                : billMapper.selectList(new LambdaQueryWrapper<Bill>().in(Bill::getBillingKey, keys))
                        .stream().map(Bill::getBillingKey).collect(Collectors.toSet());

        for (Meter meter : meters) {
            meter.setRoomCode(roomByMeter.get(meter.getId()));
            Map<String, Object> t = tenantByMeter.get(meter.getId());
            if (t != null) {
                meter.setTenantRefId(asLongOrNull(t.get("tenant_ref_id")));
                meter.setTenantName((String) t.get("tenant_name"));
            }
            Map<String, Object> r = readingByMeter.get(meter.getId());
            if (r == null) {
                meter.setBilled(false);
                continue;
            }
            Long readingId = asLong(r.get("reading_id"));
            meter.setLatestReadingId(readingId);
            meter.setCurrReading((BigDecimal) r.get("curr_reading"));
            meter.setUsageAmount((BigDecimal) r.get("usage_amount"));
            meter.setLatestFee((BigDecimal) r.get("fee"));
            LocalDateTime readTime = toDateTime(r.get("read_time"));
            meter.setLastReadTime(readTime);
            meter.setPeriodEnd(readTime == null ? null : readTime.toLocalDate());
            meter.setPeriodStart(prevReadDate.get(meter.getId()));
            meter.setBilled(billedKeys.contains(billingKey(meter.getId(), readingId)));
        }
    }

    private static Long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static Long asLongOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        return null;
    }

    /** 一次抄表最多出一张能源费账单:这个键落在 fin_bill 的唯一索引上,重复出账会被库挡掉 */
    private static String billingKey(Long meterId, Long readingId) {
        return "meter:%d:reading:%d".formatted(meterId, readingId);
    }
}
