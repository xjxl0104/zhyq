package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.FeeSnapshot;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.FeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.mapper.FeeSnapshotMapper;
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
    private final FeeSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;
    private final FeeNoticePaymentService payments;

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
        return decorate(openRaw(period));
    }

    private FeePeriod openRaw(String period) {
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
        return decorate(p);
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
        // 公摊度数手工值允许清空(清空 = 回到自动算),updateById 跳过 null,单独写
        periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getId, existed.getId())
                .set(FeePeriod::getElectricPublicManual, input.getElectricPublicManual())
                .set(FeePeriod::getWaterPublicManual, input.getWaterPublicManual()));
        touch(existed.getId());
        return require(existed.getId());
    }

    private static void validate(FeePeriod p) {
        for (BigDecimal v : new BigDecimal[]{p.getElectricGridUsage(), p.getElectricSolarUsage(), p.getElectricPrice(),
                p.getWaterTotalUsage(), p.getWaterPrice(), p.getElectricPublicManual(), p.getWaterPublicManual(),
                p.getDomesticGarbageAmount(), p.getIndustrialGarbageAmount()}) {
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
        payments.lockPeriod(id);
        requireDraft(id);
        payments.assertCanDelete(noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, id)));
        itemMapper.delete(new LambdaQueryWrapper<FeeNoticeItem>().eq(FeeNoticeItem::getFeePeriodId, id));
        noticeMapper.delete(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, id));
        snapshotMapper.delete(new LambdaQueryWrapper<FeeSnapshot>().eq(FeeSnapshot::getFeePeriodId, id));
        periodMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 工作表

    public Sheet sheet(Long periodId, String energyType) {
        return sheetOf(require(periodId), energyType);
    }

    /** 已确认出账的月份读确认时冻结的快照;草稿月份实时计算 */
    private Sheet sheetOf(FeePeriod p, String energyType) {
        if (FeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            Sheet frozen = readSnapshot(p.getId(), kindOf(energyType), Sheet.class);
            if (frozen != null) {
                return new Sheet(p, frozen.energyType(), frozen.total(), frozen.totalSource(), frozen.submeterUsage(),
                        frozen.publicUsage(), frozen.shareBase(), frozen.price(), frozen.totalFee(), frozen.rows(),
                        frozen.warnings());
            }
        }
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
        BigDecimal manual = FeeNoticeCalculator.TYPE_WATER.equals(energyType)
                ? p.getWaterPublicManual() : p.getElectricPublicManual();
        UtilityResult r = FeeNoticeCalculator.allocate(energyType, total, price, rows, manual);
        if (manual != null) {
            source = source + "(公摊度数手工填写)";
        }
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
        Map<Long, Map<String, Object>> tenantByRoom = tenantByRoom(p);

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

    /** 仅当同一承租人覆盖整个账期才自动归属；交接和不完整租期留待单独核对。 */
    private Map<Long, Map<String, Object>> tenantByRoom(FeePeriod period) {
        YearMonth month = parse(period.getPeriod());
        LocalDate start = period.getPeriodStart() == null ? month.atDay(1) : period.getPeriodStart();
        LocalDate end = period.getPeriodEnd() == null ? month.atEndOfMonth() : period.getPeriodEnd();
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (var row : jdbc.queryForList("""
                SELECT cr.room_id, MIN(t.id) AS tenant_ref_id, MIN(t.name) AS tenant_name
                FROM biz_contract_room cr
                JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status IN (5,8,9)
                JOIN biz_tenant t ON t.id = c.tenant_ref_id AND t.deleted = 0
                WHERE cr.deleted = 0 AND c.start_date <= ?
                  AND LEAST(c.end_date, COALESCE(c.terminate_date, c.end_date)) >= ?
                GROUP BY cr.room_id
                HAVING COUNT(DISTINCT t.id) = 1 AND MAX(c.start_date <= ?
                    AND LEAST(c.end_date, COALESCE(c.terminate_date, c.end_date)) >= ?) = 1
                """, end, start, start, end)) {
            out.put(((Number) row.get("room_id")).longValue(), row);
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
            // 本月读数就是下个月的上期读数:下个月已经录过的,把它的上期读数、用量、费用一起改过来
            cascadeNext(meter, p, in.currReading());
            // 没有更晚的抄表时把表计「上次读数」同步成本期读数
            Long later = readingMapper.selectCount(new LambdaQueryWrapper<Reading>()
                    .eq(Reading::getMeterId, meter.getId()).gt(Reading::getReadTime, r.getReadTime()));
            if (later == null || later == 0) {
                meterMapper.update(null, new LambdaUpdateWrapper<Meter>()
                        .eq(Meter::getId, meter.getId()).set(Meter::getLastReading, in.currReading()));
            }
            saved++;
        }
        touch(periodId);
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
            setUnitLeaseArea(meter.getId(), meter.getArea(), meter.getRoomId(), meter.getCustomerName(), leaseArea);
        }
        touchDrafts();
    }

    // ------------------------------------------------------------------ 垃圾费工作表

    public GarbageSheet garbageSheet(Long periodId) {
        return garbageOf(require(periodId));
    }

    private GarbageSheet garbageOf(FeePeriod p) {
        if (FeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            GarbageSheet frozen = readSnapshot(p.getId(), KIND_GARBAGE, GarbageSheet.class);
            if (frozen != null) {
                return new GarbageSheet(p, frozen.totalLeaseArea(), frozen.rows());
            }
        }
        return garbageLive(p);
    }

    /** 与通知单共用 FeeNoticeCalculator.splitGarbage,两边金额逐分一致 */
    private GarbageSheet garbageLive(FeePeriod p) {
        List<SheetRow> rows = new ArrayList<>();
        rows.addAll(loadRows(p, FeeNoticeCalculator.TYPE_WATER));
        rows.addAll(loadRows(p, FeeNoticeCalculator.TYPE_ELECTRIC));
        FeeNoticeCalculator.GarbageSplit split = FeeNoticeCalculator.splitGarbage(p, rows);
        Map<FeeNoticeCalculator.UnitTenant, List<SheetRow>> byUnit = new LinkedHashMap<>();
        for (SheetRow r : rows) {
            if (r.isTenant()) {
                byUnit.computeIfAbsent(new FeeNoticeCalculator.UnitTenant(FeeNoticeCalculator.unitKey(r), FeeNoticeCalculator.tenantKey(r)), k -> new ArrayList<>()).add(r);
            }
        }
        List<GarbageRow> out = new ArrayList<>();
        for (var entry : split.units().entrySet()) {
            FeeNoticeCalculator.UnitShare us = entry.getValue();
            List<SheetRow> unitRows = byUnit.get(entry.getKey());
            SheetRow first = unitRows.get(0);
            out.add(new GarbageRow(us.unit(), FeeNoticeCalculator.floorKey(first.getArea()),
                    FeeNoticeCalculator.firstNonBlank(first.getTenantName(), first.getCustomerName(), "-"),
                    us.share(), us.area(), us.ratio(), us.domestic(), us.industrial(),
                    unitRows.stream().map(SheetRow::getMeterId).distinct().toList()));
        }
        return new GarbageSheet(p, split.totalArea(), out);
    }

    // ------------------------------------------------------------------ 生成通知单

    /** 按当前读数与录入项重新生成全部通知单(幂等:先清后插,手工字段按户保留) */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(Long periodId) {
        payments.lockPeriod(periodId);
        FeePeriod p = requireDraft(periodId);
        Sheet water = sheet(p, FeeNoticeCalculator.TYPE_WATER);
        Sheet electric = sheet(p, FeeNoticeCalculator.TYPE_ELECTRIC);
        UtilityResult wr = new UtilityResult(water.energyType(), water.total(), water.submeterUsage(),
                water.publicUsage(), water.shareBase(), water.price(), water.totalFee(), water.rows(), water.warnings());
        UtilityResult er = new UtilityResult(electric.energyType(), electric.total(), electric.submeterUsage(),
                electric.publicUsage(), electric.shareBase(), electric.price(), electric.totalFee(), electric.rows(),
                electric.warnings());

        // 上一版通知单连同明细行一起带上:手工新增的收费行、手工改过的备注要按户找回
        List<FeeNotice> olds = noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                .eq(FeeNotice::getFeePeriodId, periodId));
        Map<Long, FeeNotice> oldById = new LinkedHashMap<>();
        olds.forEach(old -> oldById.put(old.getId(), old));
        for (FeeNoticeItem it : itemMapper.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getFeePeriodId, periodId).orderByAsc(FeeNoticeItem::getSort))) {
            FeeNotice owner = oldById.get(it.getNoticeId());
            if (owner != null) {
                owner.getItems().add(it);
            }
        }
        // 旧版是按楼层/区域分开出的,一个租户可能有好几张;按租户归并后再找回手工内容
        NoticeBatch batch = FeeNoticeCalculator.buildNotices(p, wr, er, FeeNoticeCalculator.mergePrevious(olds));
        Map<String, List<Long>> paymentSources = payments.prepareRegeneration(olds, batch.notices());

        itemMapper.delete(new LambdaQueryWrapper<FeeNoticeItem>().eq(FeeNoticeItem::getFeePeriodId, periodId));
        noticeMapper.delete(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, periodId));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (FeeNotice n : batch.notices()) {
            noticeMapper.insert(n);
            payments.moveAttachments(paymentSources, n);
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
        snap.setCalculationVersion(2);
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
        list.forEach(n -> byId.put(n.getId(), FeeNoticePaymentService.normalizeStatus(n)));
        for (FeeNoticeItem it : itemMapper.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getFeePeriodId, periodId).orderByAsc(FeeNoticeItem::getSort))) {
            FeeNotice n = byId.get(it.getNoticeId());
            if (n != null) {
                n.getItems().add(it);
            }
        }
        return list;
    }

    /**
     * 旧接口:只改 已付 / 前期欠费 / 其他费用(单个金额) / 租户名 / 单元名 / 备注。
     * 其他费用折成一行手工收费项,统一走 {@link #editNotice};页面上的调整弹窗用的是 editNotice。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeeNotice updateNotice(FeeNotice input) {
        List<ManualItemInput> manual = null;
        if (input.getOtherFee() != null) {
            manual = input.getOtherFee().signum() == 0 ? List.of()
                    : List.of(new ManualItemInput(StringUtils.hasText(input.getOtherFeeNote()) ? input.getOtherFeeNote() : "其他费用",
                    "元", null, null, input.getOtherFee(), null));
        }
        return editNotice(new NoticeEdit(input.getId(), input.getTenantName(), input.getUnitName(), input.getPaidAmount(),
                input.getPriorArrears(), input.getRemark(), null, manual));
    }

    /** 调整通知单时手工新增的一行收费项:金额可直接填,也可填数量 × 单价自动算 */
    public record ManualItemInput(String feeItem, String unit, BigDecimal quantity, BigDecimal unitPrice,
                                  BigDecimal amount, String remark) {}

    /**
     * 通知单整单调整:基本信息、抬头覆盖、各行备注、手工收费行一次保存。
     * itemRemarks = 自动行(水费/电费/垃圾费…)id → 新备注;manualItems = 手工收费行的完整清单(整体替换)。
     */
    public record NoticeEdit(Long id, String noticeNo, String tenantName, String unitName, BigDecimal paidAmount,
                             BigDecimal priorArrears, String remark, String headerOverrideJson,
                             Map<Long, String> itemRemarks, List<ManualItemInput> manualItems) {
        public NoticeEdit(Long id, String tenantName, String unitName, BigDecimal paidAmount,
                          BigDecimal priorArrears, String remark, Map<Long, String> itemRemarks,
                          List<ManualItemInput> manualItems) {
            this(id, null, tenantName, unitName, paidAmount, priorArrears, remark, null, itemRemarks, manualItems);
        }
    }

    private static final int MAX_MANUAL_ITEMS = 30;

    @Transactional(rollbackFor = Exception.class)
    public FeeNotice editNotice(NoticeEdit in) {
        FeeNotice n = noticeMapper.selectById(in.id());
        if (n == null) {
            throw new BizException("通知单不存在: " + in.id());
        }
        requireDraft(n.getFeePeriodId());
        if (StringUtils.hasText(in.noticeNo())) {
            n.setNoticeNo(in.noticeNo().trim());
        }
        if (StringUtils.hasText(in.tenantName())) {
            n.setTenantName(in.tenantName().trim());
        }
        if (StringUtils.hasText(in.unitName())) {
            n.setUnitName(in.unitName().trim());
        }
        if (in.paidAmount() != null) {
            n.setPaidAmount(in.paidAmount());
        }
        if (in.priorArrears() != null) {
            n.setPriorArrears(in.priorArrears());
        }
        if (in.remark() != null) {
            n.setRemark(in.remark());
        }
        if (in.headerOverrideJson() != null) {
            n.setHeaderOverrideJson(StringUtils.hasText(in.headerOverrideJson()) ? in.headerOverrideJson() : null);
        }

        List<FeeNoticeItem> current = itemMapper.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getNoticeId, n.getId()).orderByAsc(FeeNoticeItem::getSort));
        // 1) 自动行的备注(改了才记为手工备注,重新生成时才会保留)
        if (in.itemRemarks() != null) {
            for (FeeNoticeItem it : current) {
                if (it.isManualItem() || !in.itemRemarks().containsKey(it.getId())) {
                    continue;
                }
                String next = in.itemRemarks().get(it.getId());
                next = next == null ? "" : next.trim();
                if (!next.equals(it.getRemark() == null ? "" : it.getRemark())) {
                    it.setRemark(next);
                    it.setRemarkEdited(1);
                    itemMapper.updateById(it);
                }
            }
        }
        // 2) 手工收费行:整体替换
        if (in.manualItems() != null) {
            if (in.manualItems().size() > MAX_MANUAL_ITEMS) {
                throw new BizException("手工收费项最多 " + MAX_MANUAL_ITEMS + " 行");
            }
            for (FeeNoticeItem it : current) {
                if (it.isManualItem()) {
                    itemMapper.deleteById(it.getId());
                }
            }
            long top = current.stream().filter(i -> !i.isManualItem() && !i.isDetail()).count();
            int idx = 0;
            for (ManualItemInput m : in.manualItems()) {
                idx++;
                if (!StringUtils.hasText(m.feeItem())) {
                    throw new BizException("第 " + idx + " 行手工收费项没有填费用项目名称");
                }
                if (m.feeItem().trim().length() > 64) {
                    throw new BizException("第 " + idx + " 行费用项目名称太长(最多 64 字)");
                }
                BigDecimal amount = m.amount();
                if (amount == null && m.quantity() != null && m.unitPrice() != null) {
                    amount = m.quantity().multiply(m.unitPrice());
                }
                if (amount == null) {
                    throw new BizException("第 " + idx + " 行「" + m.feeItem().trim() + "」没有金额,请填金额或数量 × 单价");
                }
                FeeNoticeItem it = new FeeNoticeItem();
                it.setNoticeId(n.getId());
                it.setFeePeriodId(n.getFeePeriodId());
                it.setItemType(FeeNoticeItem.T_CUSTOM);
                it.setManual(1);
                it.setSeq(String.valueOf(top + idx));
                it.setFeeItem(m.feeItem().trim());
                it.setUnit(StringUtils.hasText(m.unit()) ? m.unit().trim() : "元");
                it.setQuantity(m.quantity());
                it.setUnitPrice(m.unitPrice());
                it.setAmount(FeeNoticeCalculator.money(amount));
                it.setRemark(StringUtils.hasText(m.remark()) ? m.remark().trim() : null);
                it.setSort(900 + idx);
                itemMapper.insert(it);
            }
        }
        // 3) 其他费用 = 手工收费行之和,小计/应收跟着重算
        List<FeeNoticeItem> after = itemMapper.selectList(new LambdaQueryWrapper<FeeNoticeItem>()
                .eq(FeeNoticeItem::getNoticeId, n.getId()).orderByAsc(FeeNoticeItem::getSort));
        FeeNoticeCalculator.applyOtherFee(n, after);
        FeeNoticeCalculator.recalcTotals(n);
        noticeMapper.updateById(n);
        n.setItems(after);
        return FeeNoticePaymentService.normalizeStatus(n);
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
        if (Boolean.TRUE.equals(p.getNoticeStale())) {
            throw new BizException("生成通知单之后读数、总量或表计又有改动,请先重新生成通知单再确认出账");
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
        freeze(p);
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
                .eq(FeeNotice::getFeePeriodId, periodId).isNotNull(FeeNotice::getBillId).orderByAsc(FeeNotice::getBillId));
        int kept = 0;
        for (FeeNotice n : notices) {
            Bill b = billMapper.lockActive(n.getBillId());
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().signum() > 0) {
                kept++;
            }
        }
        if (kept > 0) {
            throw new BizException("有 " + kept + " 张账单已收款,不能撤销;请先在收款记录里红冲后重试");
        }
        int removed = 0;
        for (FeeNotice n : notices) {
            if (billMapper.lockActive(n.getBillId()) != null) {
                billMapper.deleteById(n.getBillId());
                removed++;
            }
            noticeMapper.update(null, new LambdaUpdateWrapper<FeeNotice>()
                    .eq(FeeNotice::getId, n.getId()).set(FeeNotice::getBillId, null));
        }
        snapshotMapper.delete(new LambdaQueryWrapper<FeeSnapshot>().eq(FeeSnapshot::getFeePeriodId, periodId));
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

    // ------------------------------------------------------------------ 账期状态、冻结快照与下月衔接

    private static final String KIND_ELECTRIC = "ELECTRIC";
    private static final String KIND_WATER = "WATER";
    private static final String KIND_GARBAGE = "GARBAGE";

    /** 派生状态:生成通知单之后读数 / 总量 / 表计又改过 → 通知单是旧的 */
    private FeePeriod decorate(FeePeriod p) {
        if (p != null) {
            p.setNoticeStale(p.getGeneratedTime() != null && FeePeriod.ST_DRAFT.equals(p.getStatus())
                    && (!Integer.valueOf(2).equals(p.getCalculationVersion()) || (p.getInputsChangedTime() != null
                    && p.getInputsChangedTime().isAfter(p.getGeneratedTime()))));
        }
        return p;
    }

    public List<FeePeriod> decorate(List<FeePeriod> list) {
        list.forEach(this::decorate);
        return list;
    }

    private void touch(Long periodId) {
        periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getId, periodId).set(FeePeriod::getInputsChangedTime, LocalDateTime.now()));
    }

    /** 表计增减、改区域等影响所有未出账月份 */
    private void touchDrafts() {
        periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>()
                .eq(FeePeriod::getStatus, FeePeriod.ST_DRAFT)
                .set(FeePeriod::getInputsChangedTime, LocalDateTime.now()));
    }

    private static String kindOf(String energyType) {
        return FeeNoticeCalculator.TYPE_WATER.equals(energyType) ? KIND_WATER : KIND_ELECTRIC;
    }

    private <T> T readSnapshot(Long periodId, String kind, Class<T> type) {
        FeeSnapshot s = snapshotMapper.selectOne(new LambdaQueryWrapper<FeeSnapshot>()
                .eq(FeeSnapshot::getFeePeriodId, periodId).eq(FeeSnapshot::getKind, kind)
                .orderByDesc(FeeSnapshot::getId).last("LIMIT 1"));
        if (s == null || s.getContent() == null) {
            return null;
        }
        try {
            return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(s.getContent(), type);
        } catch (JsonProcessingException e) {
            return null; // 快照读不出来就退回实时计算,不拦住页面
        }
    }

    private void writeSnapshot(Long periodId, String kind, Object value) {
        snapshotMapper.delete(new LambdaQueryWrapper<FeeSnapshot>()
                .eq(FeeSnapshot::getFeePeriodId, periodId).eq(FeeSnapshot::getKind, kind));
        FeeSnapshot s = new FeeSnapshot();
        s.setFeePeriodId(periodId);
        s.setKind(kind);
        try {
            s.setContent(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new BizException("冻结账期数据失败,请重试");
        }
        snapshotMapper.insert(s);
    }

    /** 确认出账时把当月三张工作表冻结下来,之后表计增减不再影响这个月 */
    private void freeze(FeePeriod p) {
        Sheet e = sheet(p, FeeNoticeCalculator.TYPE_ELECTRIC);
        Sheet w = sheet(p, FeeNoticeCalculator.TYPE_WATER);
        GarbageSheet g = garbageLive(p);
        writeSnapshot(p.getId(), KIND_ELECTRIC, new Sheet(null, e.energyType(), e.total(), e.totalSource(),
                e.submeterUsage(), e.publicUsage(), e.shareBase(), e.price(), e.totalFee(), e.rows(), e.warnings()));
        writeSnapshot(p.getId(), KIND_WATER, new Sheet(null, w.energyType(), w.total(), w.totalSource(),
                w.submeterUsage(), w.publicUsage(), w.shareBase(), w.price(), w.totalFee(), w.rows(), w.warnings()));
        writeSnapshot(p.getId(), KIND_GARBAGE, new GarbageSheet(null, g.totalLeaseArea(), g.rows()));
    }

    /**
     * 本月读数就是下个月的上期读数。本月读数改了,下个月已经录过的同一块表要跟着改上期读数、用量与费用,
     * 否则抄表记录与能耗统计里下个月的用量会对不上;下个月已确认出账的不能悄悄改,直接拦下。
     */
    private void cascadeNext(Meter meter, FeePeriod p, BigDecimal curr) {
        Reading next = readingMapper.selectOne(new LambdaQueryWrapper<Reading>()
                .eq(Reading::getMeterId, meter.getId()).isNotNull(Reading::getPeriod).gt(Reading::getPeriod, p.getPeriod())
                .orderByAsc(Reading::getPeriod).orderByDesc(Reading::getId).last("LIMIT 1"));
        if (next == null) {
            return;
        }
        FeePeriod np = periodMapper.selectOne(new LambdaQueryWrapper<FeePeriod>()
                .eq(FeePeriod::getPeriod, next.getPeriod()).last("LIMIT 1"));
        if (np != null && FeePeriod.ST_CONFIRMED.equals(np.getStatus())) {
            throw new BizException("「" + np.getPeriod() + "」已确认出账,改「" + p.getPeriod()
                    + "」的读数会牵动它的上期读数;请先撤销「" + np.getPeriod() + "」的确认");
        }
        BigDecimal ratio = meter.getRatio() == null || meter.getRatio().signum() <= 0 ? BigDecimal.ONE : meter.getRatio();
        BigDecimal usage = nz(next.getCurrReading()).subtract(curr).max(BigDecimal.ZERO)
                .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        next.setPrevReading(curr);
        next.setUsageAmount(usage);
        if (np != null) {
            BigDecimal price = FeeNoticeCalculator.TYPE_WATER.equals(meter.getEnergyType())
                    ? nz(np.getWaterPrice()) : nz(np.getElectricPrice());
            next.setFee(usage.multiply(price).setScale(2, RoundingMode.HALF_UP));
        }
        readingMapper.updateById(next);
        if (np != null) {
            touch(np.getId());
        }
    }

    // ------------------------------------------------------------------ 年月表

    /** 年月表上每个月一个按钮:总用电量与电费、总用水量与水费、垃圾费、应收合计与已留存的单据数 */
    public record MonthOverview(String period, Long periodId, String status, Boolean noticeStale,
                                BigDecimal electricUsage, BigDecimal electricFee,
                                BigDecimal waterUsage, BigDecimal waterFee,
                                BigDecimal garbageTotal, BigDecimal garbageFee,
                                BigDecimal totalDue, int noticeCount,
                                int electricFiles, int waterFiles, int garbageFiles) {}

    public List<MonthOverview> overview(int year) {
        if (year < 2000 || year > 2100) {
            throw new BizException("年份不合法");
        }
        List<FeePeriod> periods = decorate(periodMapper.selectList(new LambdaQueryWrapper<FeePeriod>()
                .ge(FeePeriod::getPeriod, year + "-01").le(FeePeriod::getPeriod, year + "-12")
                .orderByAsc(FeePeriod::getPeriod)));
        if (periods.isEmpty()) {
            return List.of();
        }
        String ids = periods.stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(","));
        Map<Long, BigDecimal> dueByPeriod = new LinkedHashMap<>();
        Map<Long, Integer> countByPeriod = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT fee_period_id, COUNT(*) AS c, COALESCE(SUM(total_due), 0) AS s FROM eng_fee_notice"
                        + " WHERE deleted = 0 AND fee_period_id IN (" + ids + ") GROUP BY fee_period_id")) {
            Long id = ((Number) r.get("fee_period_id")).longValue();
            countByPeriod.put(id, ((Number) r.get("c")).intValue());
            dueByPeriod.put(id, (BigDecimal) r.get("s"));
        }
        Map<String, Integer> files = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT biz_type, biz_id, COUNT(*) AS c FROM sys_file WHERE deleted = 0"
                        + " AND biz_type IN ('energy_fee_electric','energy_fee_water','energy_fee_garbage')"
                        + " AND biz_id IN (" + ids + ") GROUP BY biz_type, biz_id")) {
            files.put(r.get("biz_type") + ":" + r.get("biz_id"), ((Number) r.get("c")).intValue());
        }
        List<MonthOverview> out = new ArrayList<>();
        for (FeePeriod p : periods) {
            Sheet e = sheetOf(p, FeeNoticeCalculator.TYPE_ELECTRIC);
            Sheet w = sheetOf(p, FeeNoticeCalculator.TYPE_WATER);
            GarbageSheet g = garbageOf(p);
            BigDecimal garbageFee = BigDecimal.ZERO;
            for (GarbageRow row : g.rows()) {
                garbageFee = garbageFee.add(nz(row.domesticFee())).add(nz(row.industrialFee()));
            }
            int count = countByPeriod.getOrDefault(p.getId(), 0);
            out.add(new MonthOverview(p.getPeriod(), p.getId(), p.getStatus(), p.getNoticeStale(),
                    nz(e.total()), e.totalFee(), nz(w.total()), w.totalFee(),
                    nz(p.getDomesticGarbageAmount()).add(nz(p.getIndustrialGarbageAmount())), garbageFee,
                    count == 0 ? null : dueByPeriod.get(p.getId()), count,
                    files.getOrDefault("energy_fee_electric:" + p.getId(), 0),
                    files.getOrDefault("energy_fee_water:" + p.getId(), 0),
                    files.getOrDefault("energy_fee_garbage:" + p.getId(), 0)));
        }
        return out;
    }

    // ------------------------------------------------------------------ 删除通知单

    /**
     * 删除通知单:指定若干张,或 all=true 一键删除本期全部。
     * 已确认出账的通知单连同它生成的账单一起删,但账单收过款的不能删(要先红冲)。
     * 删掉的户在重新生成通知单时会按读数重新出现;想永久去掉某个区域/用户,请在电费、水费页删除对应表计。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteNotices(Long periodId, List<Long> ids, boolean all) {
        payments.lockPeriod(periodId);
        FeePeriod p = require(periodId);
        LambdaQueryWrapper<FeeNotice> qw = new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, periodId);
        if (!all) {
            if (ids == null || ids.isEmpty()) {
                throw new BizException("请先选择要删除的通知单");
            }
            qw.in(FeeNotice::getId, ids);
        }
        List<FeeNotice> targets = noticeMapper.selectList(qw.orderByAsc(FeeNotice::getBillId));
        Map<String, Object> m = new LinkedHashMap<>();
        if (targets.isEmpty()) {
            m.put("deleted", 0);
            m.put("bills", 0);
            return m;
        }
        payments.assertCanDelete(targets);
        int paid = 0;
        for (FeeNotice n : targets) {
            Bill b = n.getBillId() == null ? null : billMapper.lockActive(n.getBillId());
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().signum() > 0) {
                paid++;
            }
        }
        if (paid > 0) {
            throw new BizException("有 " + paid + " 张通知单对应的账单已收款,不能删除;请先在收款记录里红冲后重试");
        }
        int bills = 0;
        List<Long> noticeIds = new ArrayList<>();
        for (FeeNotice n : targets) {
            noticeIds.add(n.getId());
            if (n.getBillId() != null && billMapper.lockActive(n.getBillId()) != null) {
                billMapper.deleteById(n.getBillId());
                bills++;
            }
        }
        itemMapper.delete(new LambdaQueryWrapper<FeeNoticeItem>().in(FeeNoticeItem::getNoticeId, noticeIds));
        noticeMapper.deleteBatchIds(noticeIds);

        List<FeeNotice> left = noticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>().eq(FeeNotice::getFeePeriodId, periodId));
        BigDecimal due = BigDecimal.ZERO;
        for (FeeNotice n : left) {
            due = due.add(nz(n.getTotalDue()));
        }
        periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>().eq(FeePeriod::getId, periodId)
                .set(FeePeriod::getNoticeCount, left.size()).set(FeePeriod::getTotalAmount, due));
        if (left.isEmpty() && FeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            snapshotMapper.delete(new LambdaQueryWrapper<FeeSnapshot>().eq(FeeSnapshot::getFeePeriodId, periodId));
            periodMapper.update(null, new LambdaUpdateWrapper<FeePeriod>().eq(FeePeriod::getId, periodId)
                    .set(FeePeriod::getStatus, FeePeriod.ST_DRAFT));
        }
        m.put("deleted", noticeIds.size());
        m.put("bills", bills);
        return m;
    }

    // ------------------------------------------------------------------ 增减区域 / 用户(表计)

    public record MeterInput(Long id, String energyType, String area, String customerName, String code,
                             BigDecimal ratio, Integer sharePublic, BigDecimal leaseArea, BigDecimal lastReading,
                             Long roomId) {}

    /**
     * 新增或修改一块租户分表(= 一个区域下的一个用户)。新增时表计编号可不填,自动按「电/水 + 区域」生成。
     * 计租面积没填且同区域已有表计填过时沿用,免得同一单元要重复填。
     */
    @Transactional(rollbackFor = Exception.class)
    public Meter saveMeter(MeterInput in) {
        String type = in.energyType() == null ? null : in.energyType().trim();
        String area = in.area() == null ? "" : in.area().trim();
        if (in.id() == null) {
            if (!FeeNoticeCalculator.TYPE_ELECTRIC.equals(type) && !FeeNoticeCalculator.TYPE_WATER.equals(type)) {
                throw new BizException("表计类型只能是「电」或「水」");
            }
            if (area.isEmpty()) {
                throw new BizException("请填写区域,如 2层D区");
            }
            validateMeter(in);
            Meter m = new Meter();
            m.setEnergyType(type);
            m.setArea(area);
            m.setCustomerName(trimToNull(in.customerName()));
            m.setName(area + (StringUtils.hasText(in.customerName()) ? " " + in.customerName().trim() : ""));
            m.setCode(StringUtils.hasText(in.code()) ? in.code().trim() : nextMeterCode(type, area));
            if (meterMapper.selectCount(new LambdaQueryWrapper<Meter>().eq(Meter::getCode, m.getCode())) > 0) {
                throw new BizException("表计编号「" + m.getCode() + "」已存在");
            }
            m.setMeterRole("TENANT");
            m.setSharePublic(in.sharePublic() != null && in.sharePublic() == 0 ? 0 : 1);
            m.setRatio(in.ratio() == null || in.ratio().signum() <= 0 ? BigDecimal.ONE : in.ratio());
            m.setLastReading(in.lastReading() == null ? BigDecimal.ZERO : in.lastReading());
            m.setRoomId(in.roomId());
            m.setStatus(1);
            m.setLeaseArea(in.leaseArea() != null ? in.leaseArea() : sameAreaLeaseArea(area, m.getRoomId(), m.getCustomerName()));
            try {
                meterMapper.insert(m);
            } catch (DuplicateKeyException e) {
                throw new BizException("表计编号「" + m.getCode() + "」已存在");
            }
            touchDrafts();
            return m;
        }
        Meter m = meterMapper.selectById(in.id());
        if (m == null) {
            throw new BizException("表计不存在: " + in.id());
        }
        validateMeter(in);
        LambdaUpdateWrapper<Meter> uw = new LambdaUpdateWrapper<Meter>().eq(Meter::getId, m.getId());
        if (!area.isEmpty()) {
            uw.set(Meter::getArea, area);
        }
        if (in.customerName() != null) {
            uw.set(Meter::getCustomerName, trimToNull(in.customerName()));
        }
        if (in.ratio() != null && in.ratio().signum() > 0) {
            uw.set(Meter::getRatio, in.ratio());
        }
        if (in.sharePublic() != null) {
            uw.set(Meter::getSharePublic, in.sharePublic() == 0 ? 0 : 1);
        }
        if (in.roomId() != null) {
            uw.set(Meter::getRoomId, in.roomId());
        }
        meterMapper.update(null, uw);
        if (in.leaseArea() != null) {
            String targetArea = area.isEmpty() ? m.getArea() : area;
            setUnitLeaseArea(m.getId(), targetArea, in.roomId() == null ? m.getRoomId() : in.roomId(),
                    in.customerName() == null ? m.getCustomerName() : trimToNull(in.customerName()), in.leaseArea());
        }
        touchDrafts();
        return meterMapper.selectById(m.getId());
    }

    /** 删除区域/用户:把这些表计逻辑删除。历史已出账月份读冻结快照,不受影响 */
    @Transactional(rollbackFor = Exception.class)
    public int deleteMeters(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择要删除的区域/用户");
        }
        int n = meterMapper.deleteBatchIds(ids);
        touchDrafts();
        return n;
    }

    private static void validateMeter(MeterInput in) {
        for (BigDecimal v : new BigDecimal[]{in.leaseArea(), in.lastReading()}) {
            if (v != null && v.signum() < 0) {
                throw new BizException("面积与读数不能为负");
            }
        }
    }

    private String nextMeterCode(String type, String area) {
        String base = (FeeNoticeCalculator.TYPE_ELECTRIC.equals(type) ? "DB-" : "SB-") + area;
        String code = base;
        int i = 2;
        while (meterMapper.selectCount(new LambdaQueryWrapper<Meter>().eq(Meter::getCode, code)) > 0) {
            code = base + "-" + i++;
        }
        return code;
    }

    private void setUnitLeaseArea(Long meterId, String area, Long roomId, String customer, BigDecimal size) {
        var update = new LambdaUpdateWrapper<Meter>().set(Meter::getLeaseArea, size);
        if (!StringUtils.hasText(area) || (roomId == null && !StringUtils.hasText(customer))) {
            update.eq(Meter::getId, meterId);
        } else {
            update.eq(Meter::getArea, area).eq(roomId != null, Meter::getRoomId, roomId)
                    .isNull(roomId == null, Meter::getRoomId).eq(roomId == null, Meter::getCustomerName, customer);
        }
        meterMapper.update(null, update);
    }

    private BigDecimal sameAreaLeaseArea(String area, Long roomId, String customer) {
        if (roomId == null && !StringUtils.hasText(customer)) return null;
        return meterMapper.selectList(new LambdaQueryWrapper<Meter>().eq(Meter::getArea, area)
                .eq(roomId != null, Meter::getRoomId, roomId).isNull(roomId == null, Meter::getRoomId)
                .eq(roomId == null, Meter::getCustomerName, customer).isNotNull(Meter::getLeaseArea))
                .stream().map(Meter::getLeaseArea).filter(a -> a.signum() > 0).max(BigDecimal::compareTo).orElse(null);
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
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
