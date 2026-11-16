package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.FeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.ReadingMapper;
import com.zhyq.park.energy.service.FeeNoticeCalculator.NoticeBatch;
import com.zhyq.park.energy.service.FeeNoticeCalculator.SheetRow;
import com.zhyq.park.energy.service.FeeNoticeCalculator.UtilityResult;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月度缴费账期:录总量 → 录读数 → 生成通知单 → 确认出账。
 *
 * <p>算法在 {@link FeeNoticeCalculator},这里只管取数、落库与状态流转。</p>
 */
@Service
@RequiredArgsConstructor
public class FeeNoticeService {

    private static final String FEE_TYPE_ENERGY = "能源费";
    private static final String DEFAULT_PARK = "DIPARK数智云仓产业园";

    private final FeePeriodMapper periodMapper;
    private final FeeNoticeMapper noticeMapper;
    private final FeeNoticeItemMapper itemMapper;
    private final MeterMapper meterMapper;
    private final ReadingMapper readingMapper;
    private final BillMapper billMapper;
    private final JdbcTemplate jdbc;

    /** 一种能源的工作表:行 + 汇总,给 电费 / 水费 页面 */
    public record Sheet(FeePeriod period, String energyType, BigDecimal total, String totalSource,
                        BigDecimal submeterUsage, BigDecimal publicUsage, BigDecimal shareBase,
                        BigDecimal price, BigDecimal totalFee, List<SheetRow> rows, List<String> warnings) {}

    /** 垃圾费工作表的一行 = 一个单元 */
    public record GarbageRow(String unit, String floorKey, String tenantName, boolean sharePublic,
                             BigDecimal leaseArea, BigDecimal ratio, BigDecimal domesticFee,
                             BigDecimal industrialFee, List<Long> meterIds) {}

    public record GarbageSheet(FeePeriod period, BigDecimal totalLeaseArea, List<GarbageRow> rows) {}

    public record ReadingInput(Long meterId, BigDecimal prevReading, BigDecimal currReading) {}

    public record GenerateResult(FeePeriod period, List<FeeNotice> notices, List<String> warnings) {}

    // ------------------------------------------------------------------ 账期

    /** 取某月账期,没有就建一条,单价与抬头沿用最近一个账期 */
    @Transactional(rollbackFor = Exception.class)
    public FeePeriod open(String period, Long ignoredProjectId) {
        // 园区只有一个,账期按月唯一;表计表里 project_id 历史上没统一填,这里不按项目过滤
        Long projectId = null;
        YearMonth ym = parse(period);
        FeePeriod existed = periodMapper.selectOne(new LambdaQueryWrapper<FeePeriod>()
                .eq(FeePeriod::getPeriod, ym.toString())
                .last("LIMIT 1"));
        if (existed != null) {
            return existed;
        }
        FeePeriod latest = periodMapper.selectOne(new LambdaQueryWrapper<FeePeriod>()
                .orderByDesc(FeePeriod::getPeriod).last("LIMIT 1"));
        FeePeriod p = new FeePeriod();
        p.setProjectId(projectId);
        p.setPeriod(ym.toString());
        p.setPeriodStart(ym.atDay(1));
        p.setPeriodEnd(ym.atEndOfMonth());
        p.setStatus(FeePeriod.ST_DRAFT);
        p.setIssueDate(LocalDate.now());
        p.setDueDate(LocalDate.now().plusDays(7));
        p.setElectricGridUsage(BigDecimal.ZERO);
        p.setElectricSolarUsage(BigDecimal.ZERO);
        p.setDomesticGarbageAmount(BigDecimal.ZERO);
        p.setIndustrialGarbageAmount(BigDecimal.ZERO);
        if (latest != null) {
            p.setParkName(latest.getParkName());
            p.setNoticePrefix(latest.getNoticePrefix());
            p.setElectricPrice(latest.getElectricPrice());
            p.setWaterPrice(latest.getWaterPrice());
            p.setDomesticGarbageNote(latest.getDomesticGarbageNote());
            p.setIndustrialGarbageNote(latest.getIndustrialGarbageNote());
            p.setContactName(latest.getContactName());
            p.setContactPhone(latest.getContactPhone());
            p.setPaymentAccount(latest.getPaymentAccount());
        } else {
            p.setParkName(DEFAULT_PARK);
            p.setNoticePrefix("DIPARK");
            p.setElectricPrice(new BigDecimal("1.03"));
            p.setWaterPrice(new BigDecimal("5.51"));
        }
        try {
            periodMapper.insert(p);
        } catch (DuplicateKeyException e) {
            return periodMapper.selectOne(new LambdaQueryWrapper<FeePeriod>()
                    .eq(FeePeriod::getPeriod, ym.toString()).last("LIMIT 1"));
        }
        return p;
    }

    public FeePeriod require(Long id) {
        FeePeriod p = periodMapper.selectById(id);
        if (p == null) {
            throw new BizException("缴费账期不存在: " + id);
        }
        return p;
    }

    private FeePeriod requireDraft(Long id) {
        FeePeriod p = require(id);
        if (FeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            throw new BizException("该账期已确认出账,请先撤销确认再改动");
        }
        return p;
    }

    /** 改账期的录入项(总度数 / 单价 / 垃圾总额 / 抬头);状态与快照列不从前端收 */
    @Transactional(rollbackFor = Exception.class)
    public FeePeriod update(FeePeriod input) {
        FeePeriod existed = requireDraft(input.getId());
        validate(input);
        FeePeriod p = new FeePeriod();
        p.setId(existed.getId());
        p.setPeriodStart(input.getPeriodStart());
        p.setPeriodEnd(input.getPeriodEnd());
        p.setParkName(input.getParkName());
        p.setNoticePrefix(input.getNoticePrefix());
        p.setElectricGridUsage(nz(input.getElectricGridUsage()));
        p.setElectricSolarUsage(nz(input.getElectricSolarUsage()));
        p.setElectricPrice(nz(input.getElectricPrice()));
        p.setWaterPrice(nz(input.getWaterPrice()));
        p.setDomesticGarbageAmount(nz(input.getDomesticGarbageAmount()));
        p.setDomesticGarbageNote(input.getDomesticGarbageNote());
        p.setIndustrialGarbageAmount(nz(input.getIndustrialGarbageAmount()));
        p.setIndustrialGarbageNote(input.getIndustrialGarbageNote());
        p.setIssueDate(input.getIssueDate());
        p.setDueDate(input.getDueDate());
        p.setContactName(input.getContactName());
        p.setContactPhone(input.getContactPhone());
        p.setPaymentAccount(input.getPaymentAccount());
        p.setRemark(input.getRemark());
        periodMapper.updateById(p);
        // 水总表度数允许清空(清空 = 回到按总表抄表取),updateById 跳过 null,单独写
        periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getId, existed.getId())
                .set(FeePeriod::getWaterTotalUsage, input.getWaterTotalUsage()));
        return require(existed.getId());
    }

    private static void validate(FeePeriod p) {
        for (BigDecimal v : new BigDecimal[]{p.getElectricGridUsage(), p.getElectricSolarUsage(), p.getElectricPrice(),
                p.getWaterTotalUsage(), p.getWaterPrice(), p.getDomesticGarbageAmount(), p.getIndustrialGarbageAmount()}) {
            if (v != null && v.signum() < 0) {
                throw new BizException("度数、单价与金额都不能为负");
            }
        }
        if (p.getPeriodStart() != null && p.getPeriodEnd() != null && p.getPeriodEnd().isBefore(p.getPeriodStart())) {
            throw new BizException("计费期间止不能早于起");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDraft(id);
        itemMapper.delete(new LambdaQueryWrapper<FeeNoticeItem>().eq(FeeNoticeItem::getFeePeriodId, id));
        noticeMapper.delete(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, id));
        periodMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 工作表

    public Sheet sheet(Long periodId, String energyType) {
        FeePeriod p = require(periodId);
        return sheet(p, energyType);
    }

    private Sheet sheet(FeePeriod p, String energyType) {
        List<SheetRow> rows = loadRows(p, energyType);
        BigDecimal total;
        String source;
        BigDecimal price;
        if (FeeNoticeCalculator.TYPE_WATER.equals(energyType)) {
            price = p.getWaterPrice();
            if (p.getWaterTotalUsage() != null) {
                total = p.getWaterTotalUsage();
                source = "手工填写";
            } else {
                total = mainMeterUsage(p, energyType);
                source = total == null ? "无总表读数" : "总表抄表";
            }
        } else {
            price = p.getElectricPrice();
            total = nz(p.getElectricGridUsage()).add(nz(p.getElectricSolarUsage()));
            source = "南网 + 光伏";
        }
        UtilityResult r = FeeNoticeCalculator.allocate(energyType, total, price, rows);
        return new Sheet(p, energyType, total, source, r.submeterUsage(), r.publicUsage(), r.shareBase(),
                r.price(), r.totalFee(), r.rows(), r.warnings());
    }

    /** 该账期内所有在用分表(租户表 + 物业表)及其本期读数;没抄的行读数为空、用量 0 */
    private List<SheetRow> loadRows(FeePeriod p, String energyType) {
        LambdaQueryWrapper<Meter> qw = new LambdaQueryWrapper<Meter>()
                .eq(Meter::getStatus, 1)
                .eq(Meter::getEnergyType, energyType)
                .in(Meter::getMeterRole, "TENANT", "PROPERTY")
                .orderByAsc(Meter::getArea).orderByAsc(Meter::getId);
        List<Meter> meters = meterMapper.selectList(qw);
        if (meters.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Map<String, Object>> readings = readingsOfPeriod(p, meters);
        Map<Long, BigDecimal> prevByMeter = lastReadingBefore(p, meters);
        Map<Long, Map<String, Object>> tenantByRoom = tenantByRoom();

        List<SheetRow> rows = new ArrayList<>();
        for (Meter m : meters) {
            SheetRow r = new SheetRow();
            r.setMeterId(m.getId());
            r.setCode(m.getCode());
            r.setName(m.getName());
            r.setArea(m.getArea());
            r.setCustomerName(m.getCustomerName());
            r.setEnergyType(m.getEnergyType());
            r.setMeterRole(m.getMeterRole());
            r.setSharePublic(m.getSharePublic() == null || m.getSharePublic() == 1);
            r.setLeaseArea(m.getLeaseArea());
            r.setRatio(m.getRatio() == null || m.getRatio().signum() <= 0 ? BigDecimal.ONE : m.getRatio());
            Map<String, Object> t = m.getRoomId() == null ? null : tenantByRoom.get(m.getRoomId());
            if (t != null) {
                r.setTenantRefId(((Number) t.get("tenant_ref_id")).longValue());
                r.setTenantName((String) t.get("tenant_name"));
            }
            Map<String, Object> rd = readings.get(m.getId());
            if (rd != null) {
                r.setReadingId(((Number) rd.get("id")).longValue());
                r.setPrevReading((BigDecimal) rd.get("prev_reading"));
                r.setCurrReading((BigDecimal) rd.get("curr_reading"));
                r.setUsage(nz((BigDecimal) rd.get("usage_amount")));
            } else {
                BigDecimal prev = prevByMeter.get(m.getId());
                r.setPrevReading(prev != null ? prev : m.getLastReading());
                r.setUsage(BigDecimal.ZERO);
            }
            rows.add(r);
        }
        return rows;
    }

    /** 本期抄表:同一块表同账期多条时取 id 最大的;老数据 period 为空的按抄表月份归 */
    private Map<Long, Map<String, Object>> readingsOfPeriod(FeePeriod p, List<Meter> meters) {
        String ids = meters.stream().map(m -> String.valueOf(m.getId())).collect(Collectors.joining(","));
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT e.id, e.meter_id, e.prev_reading, e.curr_reading, e.usage_amount FROM eng_reading e JOIN ("
                        + " SELECT meter_id, MAX(id) AS max_id FROM eng_reading"
                        + " WHERE deleted = 0 AND meter_id IN (" + ids + ")"
                        + "   AND (period = ? OR (period IS NULL AND DATE_FORMAT(read_time, '%Y-%m') = ?))"
                        + " GROUP BY meter_id) l ON l.max_id = e.id", p.getPeriod(), p.getPeriod())) {
            out.put(((Number) row.get("meter_id")).longValue(), row);
        }
        return out;
    }

    /** 账期之前最后一次抄表的本期读数,作为没抄表那些行的默认上期读数 */
    private Map<Long, BigDecimal> lastReadingBefore(FeePeriod p, List<Meter> meters) {
        String ids = meters.stream().map(m -> String.valueOf(m.getId())).collect(Collectors.joining(","));
        LocalDate start = p.getPeriodStart() != null ? p.getPeriodStart() : parse(p.getPeriod()).atDay(1);
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT meter_id, curr_reading FROM ("
                        + " SELECT meter_id, curr_reading,"
                        + "   ROW_NUMBER() OVER (PARTITION BY meter_id ORDER BY read_time DESC, id DESC) AS rn"
                        + " FROM eng_reading WHERE deleted = 0 AND meter_id IN (" + ids + ") AND read_time < ?"
                        + "   AND (period IS NULL OR period < ?)) t WHERE rn = 1",
                Timestamp.valueOf(start.atStartOfDay()), p.getPeriod())) {
            out.put(((Number) row.get("meter_id")).longValue(), (BigDecimal) row.get("curr_reading"));
        }
        return out;
    }

    /** 房间 → 在租租户:只认未删除且执行中(status=5)的合同,同房间多份按起租日取最新 */
    private Map<Long, Map<String, Object>> tenantByRoom() {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT cr.room_id, t.id AS tenant_ref_id, t.name AS tenant_name
                FROM biz_contract_room cr
                JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status = 5
                JOIN biz_tenant t ON t.id = c.tenant_ref_id AND t.deleted = 0
                WHERE cr.deleted = 0
                ORDER BY c.start_date DESC, c.id DESC
                """)) {
            out.putIfAbsent(((Number) r.get("room_id")).longValue(), r);
        }
        return out;
    }

    /** 发票总表(MAIN)当月用量;没有总表或没抄表返回 null */
    private BigDecimal mainMeterUsage(FeePeriod p, String energyType) {
        List<Meter> mains = meterMapper.selectList(new LambdaQueryWrapper<Meter>()
                .eq(Meter::getStatus, 1).eq(Meter::getEnergyType, energyType).eq(Meter::getMeterRole, "MAIN"));
        if (mains.isEmpty()) {
            return null;
        }
        Map<Long, Map<String, Object>> readings = readingsOfPeriod(p, mains);
        if (readings.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> r : readings.values()) {
            total = total.add(nz((BigDecimal) r.get("usage_amount")));
        }
        return total;
    }

    // ------------------------------------------------------------------ 录读数

    /**
     * 批量保存本期读数:同一块表同账期只有一条(库里有 uk_eng_reading_active 兜底),
     * 有就改、没有就插;用量 = (本期 − 上期) × 倍率;本期读数为空的行跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveReadings(Long periodId, List<ReadingInput> inputs) {
        FeePeriod p = requireDraft(periodId);
        if (inputs == null || inputs.isEmpty()) {
            return 0;
        }
        LocalDate end = p.getPeriodEnd() != null ? p.getPeriodEnd() : parse(p.getPeriod()).atEndOfMonth();
        LocalDateTime readTime = end.atTime(23, 59, 0);
        int saved = 0;
        for (ReadingInput in : inputs) {
            if (in == null || in.meterId() == null || in.currReading() == null) {
                continue;
            }
            Meter meter = meterMapper.selectById(in.meterId());
            if (meter == null) {
                throw new BizException("表计不存在: " + in.meterId());
            }
            BigDecimal ratio = meter.getRatio() == null || meter.getRatio().signum() <= 0 ? BigDecimal.ONE : meter.getRatio();
            BigDecimal price = FeeNoticeCalculator.TYPE_WATER.equals(meter.getEnergyType())
                    ? nz(p.getWaterPrice()) : nz(p.getElectricPrice());
            Reading existed = readingMapper.selectOne(new LambdaQueryWrapper<Reading>()
                    .eq(Reading::getMeterId, meter.getId()).eq(Reading::getPeriod, p.getPeriod()).last("LIMIT 1"));
            BigDecimal prev = in.prevReading();
            if (prev == null) {
                prev = existed != null && existed.getPrevReading() != null ? existed.getPrevReading()
                        : lastReadingBefore(p, List.of(meter)).getOrDefault(meter.getId(), nz(meter.getLastReading()));
            }
            BigDecimal usage = in.currReading().subtract(prev).max(BigDecimal.ZERO)
                    .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            Reading r = existed != null ? existed : new Reading();
            r.setMeterId(meter.getId());
            r.setPeriod(p.getPeriod());
            r.setPrevReading(prev);
            r.setCurrReading(in.currReading());
            r.setUsageAmount(usage);
            r.setFee(usage.multiply(price).setScale(2, RoundingMode.HALF_UP));
            if (existed == null) {
                r.setReadSource("人工");
                r.setReadTime(readTime);
                readingMapper.insert(r);
            } else {
                readingMapper.updateById(r);
            }
            // 没有更晚的抄表时把表计「上次读数」同步成本期读数
            Long later = readingMapper.selectCount(new LambdaQueryWrapper<Reading>()
                    .eq(Reading::getMeterId, meter.getId()).gt(Reading::getReadTime, r.getReadTime()));
            if (later == null || later == 0) {
                meterMapper.update(null, new LambdaUpdateWrapper<Meter>()
                        .eq(Meter::getId, meter.getId()).set(Meter::getLastReading, in.currReading()));
            }
            saved++;
        }
        return saved;
    }

    /** 改表计的「参与公摊」与「计租面积」(同区域的表一起改面积) */
    @Transactional(rollbackFor = Exception.class)
    public void updateMeterFlags(Long meterId, Integer sharePublic, BigDecimal leaseArea, boolean applyAreaToUnit) {
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new BizException("表计不存在: " + meterId);
        }
        if (leaseArea != null && leaseArea.signum() < 0) {
            throw new BizException("计租面积不能为负");
        }
        LambdaUpdateWrapper<Meter> uw = new LambdaUpdateWrapper<Meter>().eq(Meter::getId, meterId);
        if (sharePublic != null) {
            uw.set(Meter::getSharePublic, sharePublic == 0 ? 0 : 1);
        }
        if (leaseArea != null) {
            uw.set(Meter::getLeaseArea, leaseArea);
        }
        if (sharePublic != null || leaseArea != null) {
            meterMapper.update(null, uw);
        }
        if (leaseArea != null && applyAreaToUnit && StringUtils.hasText(meter.getArea())) {
            meterMapper.update(null, new LambdaUpdateWrapper<Meter>()
                    .eq(Meter::getArea, meter.getArea()).set(Meter::getLeaseArea, leaseArea));
        }
    }

    // ------------------------------------------------------------------ 垃圾费工作表

    public GarbageSheet garbageSheet(Long periodId) {
        FeePeriod p = require(periodId);
        List<SheetRow> rows = new ArrayList<>();
        rows.addAll(loadRows(p, FeeNoticeCalculator.TYPE_WATER));
        rows.addAll(loadRows(p, FeeNoticeCalculator.TYPE_ELECTRIC));
        Map<String, List<SheetRow>> byUnit = new LinkedHashMap<>();
        for (SheetRow r : rows) {
            if (r.isTenant()) {
                byUnit.computeIfAbsent(FeeNoticeCalculator.unitKey(r), k -> new ArrayList<>()).add(r);
            }
        }
        BigDecimal totalArea = BigDecimal.ZERO;
        Map<String, BigDecimal> areaByUnit = new LinkedHashMap<>();
        for (Map.Entry<String, List<SheetRow>> e : byUnit.entrySet()) {
            BigDecimal area = e.getValue().stream().map(SheetRow::getLeaseArea)
                    .filter(a -> a != null && a.signum() > 0).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            areaByUnit.put(e.getKey(), area);
            if (e.getValue().stream().anyMatch(SheetRow::isSharePublic)) {
                totalArea = totalArea.add(area);
            }
        }
        List<GarbageRow> out = new ArrayList<>();
        for (Map.Entry<String, List<SheetRow>> e : byUnit.entrySet()) {
            SheetRow first = e.getValue().get(0);
            boolean share = e.getValue().stream().anyMatch(SheetRow::isSharePublic);
            BigDecimal area = areaByUnit.get(e.getKey());
            BigDecimal ratio = BigDecimal.ZERO;
            BigDecimal domestic = BigDecimal.ZERO;
            BigDecimal industrial = BigDecimal.ZERO;
            if (share && totalArea.signum() > 0 && area.signum() > 0) {
                ratio = area.divide(totalArea, 8, RoundingMode.HALF_UP);
                domestic = FeeNoticeCalculator.money(nz(p.getDomesticGarbageAmount()).multiply(area)
                        .divide(totalArea, 10, RoundingMode.HALF_UP));
                industrial = FeeNoticeCalculator.money(nz(p.getIndustrialGarbageAmount()).multiply(area)
                        .divide(totalArea, 10, RoundingMode.HALF_UP));
            }
            out.add(new GarbageRow(e.getKey(), FeeNoticeCalculator.floorKey(first.getArea()),
                    FeeNoticeCalculator.firstNonBlank(first.getTenantName(), first.getCustomerName(), "-"),
                    share, area, ratio, domestic, industrial,
                    e.getValue().stream().map(SheetRow::getMeterId).distinct().toList()));
        }
        return new GarbageSheet(p, totalArea, out);
    }

    // ------------------------------------------------------------------ 生成通知单

    /** 按当前读数与录入项重新生成全部通知单(幂等:先清后插,手工字段按户保留) */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(Long periodId) {
        FeePeriod p = requireDraft(periodId);
        Sheet water = sheet(p, FeeNoticeCalculator.TYPE_WATER);
        Sheet electric = sheet(p, FeeNoticeCalculator.TYPE_ELECTRIC);
        UtilityResult wr = new UtilityResult(water.energyType(), water.total(), water.submeterUsage(),
                water.publicUsage(), water.shareBase(), water.price(), water.totalFee(), water.rows(), water.warnings());
        UtilityResult er = new UtilityResult(electric.energyType(), electric.total(), electric.submeterUsage(),
                electric.publicUsage(), electric.shareBase(), electric.price(), electric.totalFee(), electric.rows(),
                electric.warnings());

        Map<String, FeeNotice> previous = new LinkedHashMap<>();
        for (FeeNotice old : noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                .eq(FeeNotice::getFeePeriodId, periodId))) {
            previous.putIfAbsent(FeeNoticeCalculator.noticeKey(old), old);
        }
        NoticeBatch batch = FeeNoticeCalculator.buildNotices(p, wr, er, previous);

        itemMapper.delete(new LambdaQueryWrapper<FeeNoticeItem>().eq(FeeNoticeItem::getFeePeriodId, periodId));
        noticeMapper.delete(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, periodId));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (FeeNotice n : batch.notices()) {
            noticeMapper.insert(n);
            for (FeeNoticeItem it : n.getItems()) {
                it.setNoticeId(n.getId());
                it.setFeePeriodId(periodId);
                itemMapper.insert(it);
            }
            totalAmount = totalAmount.add(nz(n.getTotalDue()));
        }

        FeePeriod snap = new FeePeriod();
        snap.setId(periodId);
        snap.setElectricSubmeterUsage(electric.submeterUsage());
        snap.setElectricPublicUsage(electric.publicUsage());
        snap.setWaterUsedTotal(nz(water.total()));
        snap.setWaterSubmeterUsage(water.submeterUsage());
        snap.setWaterPublicUsage(water.publicUsage());
        snap.setTotalLeaseArea(batch.totalLeaseArea());
        snap.setNoticeCount(batch.notices().size());
        snap.setTotalAmount(totalAmount);
        snap.setGeneratedTime(LocalDateTime.now());
        periodMapper.updateById(snap);
        return new GenerateResult(require(periodId), batch.notices(), batch.warnings());
    }

    public List<FeeNotice> notices(Long periodId) {
        List<FeeNotice> list = noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                .eq(FeeNotice::getFeePeriodId, periodId).orderByAsc(FeeNotice::getSort).orderByAsc(FeeNotice::getId));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, FeeNotice> byId = new LinkedHashMap<>();
        list.forEach(n -> byId.put(n.getId(), n));
        for (FeeNoticeItem it : itemMapper.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getFeePeriodId, periodId).orderByAsc(FeeNoticeItem::getSort))) {
            FeeNotice n = byId.get(it.getNoticeId());
            if (n != null) {
                n.getItems().add(it);
            }
        }
        return list;
    }

    /** 通知单上允许手工改的几项:已付 / 前期欠费 / 其他费用 / 租户名 / 单元名 / 备注 */
    @Transactional(rollbackFor = Exception.class)
    public FeeNotice updateNotice(FeeNotice input) {
        FeeNotice n = noticeMapper.selectById(input.getId());
        if (n == null) {
            throw new BizException("通知单不存在: " + input.getId());
        }
        requireDraft(n.getFeePeriodId());
        if (input.getPaidAmount() != null) {
            n.setPaidAmount(input.getPaidAmount());
        }
        if (input.getPriorArrears() != null) {
            n.setPriorArrears(input.getPriorArrears());
        }
        if (input.getOtherFee() != null) {
            n.setOtherFee(input.getOtherFee());
        }
        if (input.getOtherFeeNote() != null) {
            n.setOtherFeeNote(input.getOtherFeeNote());
        }
        if (StringUtils.hasText(input.getTenantName())) {
            n.setTenantName(input.getTenantName().trim());
        }
        if (StringUtils.hasText(input.getUnitName())) {
            n.setUnitName(input.getUnitName().trim());
        }
        if (input.getRemark() != null) {
            n.setRemark(input.getRemark());
        }
        FeeNoticeCalculator.recalcTotals(n);
        noticeMapper.updateById(n);
        syncOtherItem(n);
        return n;
    }

    /** 「其他费用」行跟着金额走:有就改/插,为 0 就删 */
    private void syncOtherItem(FeeNotice n) {
        FeeNoticeItem other = itemMapper.selectOne(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getNoticeId, n.getId()).eq(FeeNoticeItem::getItemType, FeeNoticeItem.T_OTHER)
                .last("LIMIT 1"));
        if (nz(n.getOtherFee()).signum() == 0) {
            if (other != null) {
                itemMapper.deleteById(other.getId());
            }
            return;
        }
        if (other == null) {
            Long count = itemMapper.selectCount(new LambdaQueryWrapper<FeeNoticeItem>()
                    .eq(FeeNoticeItem::getNoticeId, n.getId()).notLike(FeeNoticeItem::getItemType, "_DETAIL"));
            other = new FeeNoticeItem();
            other.setNoticeId(n.getId());
            other.setFeePeriodId(n.getFeePeriodId());
            other.setItemType(FeeNoticeItem.T_OTHER);
            other.setSeq(String.valueOf(count + 1));
            other.setUnit("元");
            other.setSort(999);
        }
        other.setFeeItem(StringUtils.hasText(n.getOtherFeeNote()) ? n.getOtherFeeNote().trim() : "其他费用");
        other.setAmount(FeeNoticeCalculator.money(n.getOtherFee()));
        if (other.getId() == null) {
            itemMapper.insert(other);
        } else {
            itemMapper.updateById(other);
        }
    }

    // ------------------------------------------------------------------ 出账

    /** 确认出账:每张能关联到租户的通知单生成一张能源费账单(幂等键 fee-notice:{id}),账期锁定 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long periodId) {
        FeePeriod p = requireDraft(periodId);
        List<FeeNotice> notices = notices(periodId);
        if (notices.isEmpty()) {
            throw new BizException("还没有生成通知单,请先点「生成通知单」");
        }
        YearMonth ym = parse(p.getPeriod());
        int inserted = 0;
        int skipped = 0;
        for (FeeNotice n : notices) {
            if (n.getTenantRefId() == null || nz(n.getSubtotal()).signum() <= 0) {
                skipped++;
                continue;
            }
            String key = "fee-notice:" + n.getId();
            Bill existed = billMapper.selectOne(new LambdaQueryWrapper<Bill>().eq(Bill::getBillingKey, key).last("LIMIT 1"));
            if (existed != null) {
                n.setBillId(existed.getId());
                noticeMapper.updateById(n);
                skipped++;
                continue;
            }
            Bill b = new Bill();
            b.setCode("UT" + ym.toString().replace("-", "") + "-" + n.getId());
            b.setBillingKey(key);
            b.setTenantRefId(n.getTenantRefId());
            b.setProjectId(p.getProjectId());
            b.setDirection(1);
            b.setFeeType(FEE_TYPE_ENERGY);
            b.setSource("抄表");
            b.setStatus(3);
            b.setAmount(nz(n.getSubtotal()));
            b.setPaidAmount(BigDecimal.ZERO);
            b.setLateFee(BigDecimal.ZERO);
            b.setPeriodStart(p.getPeriodStart() != null ? p.getPeriodStart() : ym.atDay(1));
            b.setPeriodEnd(p.getPeriodEnd() != null ? p.getPeriodEnd() : ym.atEndOfMonth());
            b.setDueDate(p.getDueDate() != null ? p.getDueDate() : ym.atEndOfMonth().plusDays(7));
            b.setInvoiceStatus(0);
            b.setRemark("%s %s 缴费通知单 %s:水费 %s + 公摊水费 %s + 电费 %s + 公摊电费 %s + 生活垃圾 %s + 工业垃圾 %s + 其他 %s".formatted(
                    p.getPeriod(), n.getUnitName(), n.getNoticeNo(),
                    nz(n.getWaterFee()).toPlainString(), nz(n.getWaterPublicFee()).toPlainString(),
                    nz(n.getElectricFee()).toPlainString(), nz(n.getElectricPublicFee()).toPlainString(),
                    nz(n.getDomesticGarbageFee()).toPlainString(), nz(n.getIndustrialGarbageFee()).toPlainString(),
                    nz(n.getOtherFee()).toPlainString()));
            billMapper.insert(b);
            n.setBillId(b.getId());
            noticeMapper.updateById(n);
            inserted++;
        }
        int updated = periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getId, periodId).eq(FeePeriod::getStatus, FeePeriod.ST_DRAFT)
                .set(FeePeriod::getStatus, FeePeriod.ST_CONFIRMED));
        if (updated == 0) {
            throw new BizException("账期状态已变化,请刷新后重试");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inserted", inserted);
        m.put("skipped", skipped);
        return m;
    }

    /** 撤销确认:删掉未收款的账单,账期放回草稿;有已收款的账单直接拦下 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revoke(Long periodId) {
        FeePeriod p = require(periodId);
        if (!FeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            throw new BizException("该账期不是已确认状态");
        }
        List<FeeNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                .eq(FeeNotice::getFeePeriodId, periodId).isNotNull(FeeNotice::getBillId));
        int kept = 0;
        for (FeeNotice n : notices) {
            Bill b = billMapper.selectById(n.getBillId());
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().signum() > 0) {
                kept++;
            }
        }
        if (kept > 0) {
            throw new BizException("有 " + kept + " 张账单已收款,不能撤销;请先在收款记录里红冲后重试");
        }
        int removed = 0;
        for (FeeNotice n : notices) {
            if (billMapper.selectById(n.getBillId()) != null) {
                billMapper.deleteById(n.getBillId());
                removed++;
            }
            noticeMapper.update(null, new LambdaUpdateWrapper<FeeNotice>()
                    .eq(FeeNotice::getId, n.getId()).set(FeeNotice::getBillId, null));
        }
        int updated = periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getId, periodId).eq(FeePeriod::getStatus, FeePeriod.ST_CONFIRMED)
                .set(FeePeriod::getStatus, FeePeriod.ST_DRAFT));
        if (updated == 0) {
            throw new BizException("账期状态已变化,请刷新后重试");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("removed", removed);
        return m;
    }

    // ------------------------------------------------------------------ 工具

    private static YearMonth parse(String period) {
        try {
            return YearMonth.parse(period);
        } catch (Exception e) {
            throw new BizException("账期格式应为 yyyy-MM,如 " + YearMonth.now());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
