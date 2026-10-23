package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktYunshanDailyVolume;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktYunshanDailyVolumeMapper;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 云山每日发货票数 → 计佣汇总订单。
 * 每个（客户关联, 发货日）对应若干条 sourceNo 以 YS-{关联ID}-{yyyyMMdd}- 开头的出库类订单，
 * 一条订单代表 N 票；佣金 = P4 每单金额 × 票数。只算佣金，不生成服务费，也不进服务费账单。
 * 云山事后修正票数：增加则补发差额订单；减少则作废该日原订单（已结算的生成扣回）后按新票数重建。
 */
@Slf4j
@Service
public class MktYunshanVolumeOrderService {
    public static final String PREFIX = "YS-";
    private static final String MODULE = "marketing";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long MAX_DAILY_TICKETS = 1_000_000L;

    public record Result(int created, int rebuilt, int unchanged, int skipped, int failed, List<String> errors) {}

    private enum Outcome { CREATED, REBUILT, UNCHANGED, SKIPPED }

    private final MktYunshanVolumeMapMapper mapMapper;
    private final MktYunshanDailyVolumeMapper volumeMapper;
    private final MktServiceContractMapper contractMapper;
    private final CustomerMapper customerMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktCommissionService commissionService;
    private final MktCustomerPricingService pricingService;
    private final MktContractTermsService termsService;
    private final BizSettings bizSettings;
    private final MktAuditService auditService;
    private final MktPromoterMapper promoterMapper;
    private final TransactionTemplate transaction;
    private final ReentrantLock lock = new ReentrantLock();

    public MktYunshanVolumeOrderService(MktYunshanVolumeMapMapper mapMapper, MktYunshanDailyVolumeMapper volumeMapper,
                                        MktServiceContractMapper contractMapper, CustomerMapper customerMapper,
                                        MktWarehouseMapper warehouseMapper, MktReferralOrderMapper orderMapper,
                                        MktCommissionService commissionService, MktCustomerPricingService pricingService,
                                        MktContractTermsService termsService, BizSettings bizSettings,
                                        MktAuditService auditService, MktPromoterMapper promoterMapper,
                                        PlatformTransactionManager transactionManager) {
        this.mapMapper = mapMapper; this.volumeMapper = volumeMapper; this.contractMapper = contractMapper;
        this.customerMapper = customerMapper; this.warehouseMapper = warehouseMapper; this.orderMapper = orderMapper;
        this.commissionService = commissionService; this.pricingService = pricingService; this.termsService = termsService;
        this.bizSettings = bizSettings; this.auditService = auditService; this.promoterMapper = promoterMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 最近一次运行（定时或手动）的结果，供页面提示失败；服务重启后清空，下一次运行再更新。 */
    public record LastRun(LocalDateTime at, String trigger, Result result) {}
    private volatile LastRun lastRun;
    public LastRun lastRun() { return lastRun; }

    /** 定时任务只自动处理最近 AUTO_WINDOW_DAYS 天；更早的历史必须先看回补预览、确认价格和链路后再手动回补。 */
    public static final int AUTO_WINDOW_DAYS = 35;

    public Result generateScheduled() {
        Result result = run(null, LocalDate.now(SHANGHAI).minusDays(AUTO_WINDOW_DAYS), null);
        lastRun = new LastRun(LocalDateTime.now(SHANGHAI), "定时", result);
        return result;
    }

    /** onlyMapId / start / end 均可为空；只处理启用中的关联，且不晚于昨天（当天统计次日才生成）。手动调用，不受自动窗口限制。 */
    public Result generate(Long onlyMapId, LocalDate start, LocalDate end) {
        Result result = run(onlyMapId, start, end);
        lastRun = new LastRun(LocalDateTime.now(SHANGHAI), "手动", result);
        return result;
    }

    private Result run(Long onlyMapId, LocalDate start, LocalDate end) {
        lock.lock();
        try {
            final LocalDate lastDay = LocalDate.now(SHANGHAI).minusDays(1);
            final LocalDate until = end == null || end.isAfter(lastDay) ? lastDay : end;
            int created = 0, rebuilt = 0, unchanged = 0, skipped = 0, failed = 0;
            List<String> errors = new ArrayList<>();
            List<MktYunshanVolumeMap> maps = mapMapper.selectList(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                    .eq(onlyMapId != null, MktYunshanVolumeMap::getId, onlyMapId)
                    .eq(MktYunshanVolumeMap::getStatus, 1).orderByAsc(MktYunshanVolumeMap::getId));
            for (MktYunshanVolumeMap map : maps) {
                List<MktYunshanDailyVolume> rows = volumeMapper.selectList(new LambdaQueryWrapper<MktYunshanDailyVolume>()
                        .eq(MktYunshanDailyVolume::getMapId, map.getId())
                        .ge(start != null, MktYunshanDailyVolume::getStatDate, start)
                        .le(MktYunshanDailyVolume::getStatDate, until)
                        .orderByAsc(MktYunshanDailyVolume::getStatDate));
                for (MktYunshanDailyVolume row : rows) {
                    try {
                        Outcome outcome = transaction.execute(status -> reconcileDay(map, row));
                        if (outcome == Outcome.CREATED) created++;
                        else if (outcome == Outcome.REBUILT) rebuilt++;
                        else if (outcome == Outcome.UNCHANGED) unchanged++;
                        else skipped++;
                    } catch (RuntimeException failure) {
                        failed++;
                        if (errors.size() < 20) errors.add("关联 " + map.getId() + " · " + row.getStatDate() + "：" + failure.getMessage());
                        log.warn("[yunshan-order] 关联 {} {} 生成失败: {}", map.getId(), row.getStatDate(), failure.getMessage());
                    }
                }
            }
            log.info("[yunshan-order] 新建 {} 日，重建 {} 日，无变化 {} 日，跳过 {} 日，失败 {} 日", created, rebuilt, unchanged, skipped, failed);
            return new Result(created, rebuilt, unchanged, skipped, failed, List.copyOf(errors));
        } finally {
            lock.unlock();
        }
    }

    /** 某天的发货票数与已生成佣金的票数。 */
    public record DayStatus(LocalDate statDate, long tickets, long commissionTickets, int orders) {}

    /** 按天核对：有云山快照的每一天，已生成多少票的计佣订单（用于判断“没生成”是没拉到数据还是没算佣金）。 */
    public List<DayStatus> dailyStatus(Long mapId, LocalDate start, LocalDate end) {
        if (mapId == null || start == null || end == null || start.isAfter(end)) throw new BizException("请选择客户关联和有效的日期区间");
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 > 92) throw new BizException("单次最多查询 92 天");
        MktYunshanVolumeMap map = mapMapper.selectById(mapId);
        if (map == null) throw new BizException("发货量客户关联不存在");
        List<MktYunshanDailyVolume> rows = volumeMapper.selectList(new LambdaQueryWrapper<MktYunshanDailyVolume>()
                .eq(MktYunshanDailyVolume::getMapId, mapId).between(MktYunshanDailyVolume::getStatDate, start, end)
                .orderByAsc(MktYunshanDailyVolume::getStatDate));
        java.util.Map<String, long[]> byDay = confirmedByDay(map);
        List<DayStatus> out = new ArrayList<>();
        for (MktYunshanDailyVolume row : rows) {
            long[] sum = byDay.getOrDefault(row.getStatDate().format(DateTimeFormatter.BASIC_ISO_DATE), new long[2]);
            out.add(new DayStatus(row.getStatDate(), row.getConsignNum() == null ? 0 : row.getConsignNum(), sum[0], (int) sum[1]));
        }
        return out;
    }

    /** 该客户关联已生成的、已确认的汇总订单：yyyyMMdd → {票数, 订单数}。 */
    private java.util.Map<String, long[]> confirmedByDay(MktYunshanVolumeMap map) {
        String prefix = PREFIX + map.getId() + "-";
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getWarehouseId, map.getWarehouseId())
                .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .likeRight(MktReferralOrder::getSourceNo, prefix));
        java.util.Map<String, long[]> byDay = new java.util.HashMap<>();
        for (MktReferralOrder order : orders) {
            String no = order.getSourceNo();
            if (no == null || no.length() < prefix.length() + 8) continue;
            long[] sum = byDay.computeIfAbsent(no.substring(prefix.length(), prefix.length() + 8), k -> new long[2]);
            sum[0] += order.getCommissionOrderCount() == null ? 0 : order.getCommissionOrderCount();
            sum[1]++;
        }
        return byDay;
    }

    public record MemberShare(Long promoterId, String name, String positionCode, String role, BigDecimal perOrder, BigDecimal estimated) {}

    public record BackfillPart(Long contractId, String contractNo, Integer signMode, String sellerName, int days, long tickets,
                               LocalDate firstDay, LocalDate lastDay, BigDecimal totalPerOrder, BigDecimal platformPerOrder,
                               BigDecimal memberPerOrder, List<MemberShare> members, BigDecimal estimatedTotal,
                               BigDecimal estimatedMembers, String blocked) {}

    public record BackfillPreview(Long mapId, String customerName, LocalDate windowFrom, LocalDate rangeFrom, LocalDate rangeTo,
                                  int pendingDays, long pendingTickets, int skippedDays, List<BackfillPart> parts) {}

    private static final class Pending {
        final MktServiceContract contract; int days; long tickets; LocalDate first, last;
        Pending(MktServiceContract contract) { this.contract = contract; }
    }

    /**
     * 历史回补预览：自动任务只处理最近 AUTO_WINDOW_DAYS 天，更早且还没生成佣金的日期要先看这里——
     * 每个合同将回补多少天、多少票，按“当前”的每单总佣金、平台留存和各成员分成估算金额，确认无误再手动回补。
     * 回补用的是确认当时的价格和分成链路，不是发货当天的。
     */
    public BackfillPreview backfillPreview(Long mapId) {
        MktYunshanVolumeMap map = mapId == null ? null : mapMapper.selectById(mapId);
        if (map == null) throw new BizException("发货量客户关联不存在");
        LocalDate windowFrom = LocalDate.now(SHANGHAI).minusDays(AUTO_WINDOW_DAYS);
        List<MktYunshanDailyVolume> rows = volumeMapper.selectList(new LambdaQueryWrapper<MktYunshanDailyVolume>()
                .eq(MktYunshanDailyVolume::getMapId, mapId).lt(MktYunshanDailyVolume::getStatDate, windowFrom)
                .orderByAsc(MktYunshanDailyVolume::getStatDate));
        java.util.Map<String, long[]> done = confirmedByDay(map);
        java.util.Map<Long, Pending> byContract = new java.util.LinkedHashMap<>();
        int skipped = 0, pendingDays = 0; long pendingTickets = 0; LocalDate first = null;
        for (MktYunshanDailyVolume row : rows) {
            long tickets = row.getConsignNum() == null ? 0 : row.getConsignNum();
            long pending = tickets - done.getOrDefault(row.getStatDate().format(DateTimeFormatter.BASIC_ISO_DATE), new long[2])[0];
            if (pending <= 0) continue;
            MktServiceContract contract = resolveContract(map, row.getStatDate());
            if (contract == null || hasOtherCommissionOrders(contract, row.getStatDate())) { skipped++; continue; }
            Pending part = byContract.computeIfAbsent(contract.getId(), id -> new Pending(contract));
            part.days++; part.tickets += pending;
            if (part.first == null) part.first = row.getStatDate();
            part.last = row.getStatDate();
            pendingDays++; pendingTickets += pending;
            if (first == null) first = row.getStatDate();
        }
        Customer customer = customerMapper.selectById(map.getCustomerId());
        List<BackfillPart> parts = new ArrayList<>();
        for (Pending part : byContract.values()) parts.add(describe(part, map, customer));
        return new BackfillPreview(mapId, customer == null ? null : customer.getName(), windowFrom, first, windowFrom.minusDays(1),
                pendingDays, pendingTickets, skipped, List.copyOf(parts));
    }

    private BackfillPart describe(Pending part, MktYunshanVolumeMap map, Customer customer) {
        MktServiceContract contract = part.contract;
        Long sellerId = contract.getPartnerId() != null ? contract.getPartnerId() : customer == null ? null : customer.getReferrerId();
        var seller = sellerId == null ? null : promoterMapper.selectById(sellerId);
        BigDecimal tickets = BigDecimal.valueOf(part.tickets);
        try {
            MktCustomerPricingService.PricingSnapshot pricing = pricingService.forAccrual(map.getCustomerId(), contract.getId());
            List<MemberShare> members = new ArrayList<>();
            var owner = promoterMapper.selectById(pricing.ownerPromoterId());
            if (pricing.ownerPerOrder().signum() > 0) {
                members.add(new MemberShare(pricing.ownerPromoterId(), owner == null ? null : owner.getName(), pricing.ownerPositionCode(),
                        "P4 本人", pricing.ownerPerOrder(), pricing.ownerPerOrder().multiply(tickets)));
            }
            for (MktCustomerPricingService.Beneficiary b : pricing.beneficiaries()) {
                members.add(new MemberShare(b.promoterId(), b.promoterName(), b.positionCode(), "链路成员", b.amountPerOrder(), b.amountPerOrder().multiply(tickets)));
            }
            BigDecimal memberPerOrder = pricing.totalPerOrder().subtract(pricing.companyPerOrder());
            return new BackfillPart(contract.getId(), contract.getContractNo(), contract.getSignMode(), seller == null ? null : seller.getName(),
                    part.days, part.tickets, part.first, part.last, pricing.totalPerOrder(), pricing.companyPerOrder(), memberPerOrder,
                    List.copyOf(members), pricing.totalPerOrder().multiply(tickets), memberPerOrder.multiply(tickets), null);
        } catch (BizException blocked) {
            return new BackfillPart(contract.getId(), contract.getContractNo(), contract.getSignMode(), seller == null ? null : seller.getName(),
                    part.days, part.tickets, part.first, part.last, null, null, null, List.of(), null, null, blocked.getMessage());
        }
    }

    private Outcome reconcileDay(MktYunshanVolumeMap map, MktYunshanDailyVolume row) {
        LocalDate day = row.getStatDate();
        long tickets = row.getConsignNum() == null ? 0 : row.getConsignNum();
        if (tickets < 0 || tickets > MAX_DAILY_TICKETS) throw new BizException("日票数异常: " + tickets);
        String prefix = PREFIX + map.getId() + "-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getWarehouseId, map.getWarehouseId())
                .likeRight(MktReferralOrder::getSourceNo, prefix)
                .last("FOR UPDATE"));
        List<MktReferralOrder> active = orders.stream()
                .filter(o -> Integer.valueOf(MktCommissionService.ORDER_CONFIRMED).equals(o.getStatus())).toList();
        long done = active.stream().mapToLong(o -> o.getCommissionOrderCount() == null ? 0 : o.getCommissionOrderCount()).sum();
        if (tickets == done) return Outcome.UNCHANGED;

        Outcome outcome = Outcome.CREATED;
        long toCreate = tickets - done;
        if (tickets < done) {
            // 票数被云山下调：作废该日全部原订单（未结算的佣金作废，已结算的生成扣回），再按新票数重建。
            for (MktReferralOrder order : active) {
                int updated = orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                        .eq(MktReferralOrder::getId, order.getId())
                        .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                        .set(MktReferralOrder::getStatus, MktCommissionService.ORDER_CANCELLED)
                        .set(MktReferralOrder::getRemark, "云山发货量修正为 " + tickets + " 票，原汇总订单作废"));
                if (updated != 1) throw new BizException("订单状态已变化，请重试");
                commissionService.clawback(order.getId(), "云山发货量修正为 " + tickets + " 票");
            }
            toCreate = tickets;
            outcome = Outcome.REBUILT;
            if (toCreate == 0) return outcome;
        }

        MktServiceContract contract = resolveContract(map, day);
        if (contract == null) {
            if (outcome == Outcome.REBUILT) return outcome;
            return Outcome.SKIPPED;
        }
        // 同一天这份合同已经由手工导入/ERP 推送的出库单计过佣金时，不再按发货票数重复计佣（宁可少算，不能多发）
        if (hasOtherCommissionOrders(contract, day)) {
            log.warn("[yunshan-order] 合同 {} {} 已有其他渠道的计佣出库单，跳过按发货量计佣，请业务确认只用一种渠道", contract.getId(), day);
            return outcome == Outcome.REBUILT ? outcome : Outcome.SKIPPED;
        }
        Customer customer = customerMapper.selectById(map.getCustomerId());
        if (customer == null) return outcome == Outcome.REBUILT ? outcome : Outcome.SKIPPED;
        Long seller = contract.getPartnerId() != null ? contract.getPartnerId() : customer.getReferrerId();
        if (seller == null) return outcome == Outcome.REBUILT ? outcome : Outcome.SKIPPED;
        MktWarehouse warehouse = warehouseMapper.selectById(contract.getWarehouseId());
        if (warehouse == null || !Integer.valueOf(5).equals(warehouse.getJoinStatus()))
            return outcome == Outcome.REBUILT ? outcome : Outcome.SKIPPED;

        // 客户或合同未配置每单佣金时这里抛出业务异常，记为失败；配置后下次运行会自动补上。
        MktCustomerPricingService.PricingSnapshot pricing = pricingService.forAccrual(customer.getId(), contract.getId());
        int count = Math.toIntExact(toCreate);
        BigDecimal pool = pricing.totalPerOrder().subtract(pricing.companyPerOrder()).multiply(BigDecimal.valueOf(count));
        int freezeDays = bizSettings.getInt(MODULE, "freeze_days", 7);
        if (freezeDays < 0) throw new BizException("冻结天数配置不合法");
        LocalDateTime eventTime = day.atStartOfDay();
        String grade = StringUtils.hasText(contract.getGrade()) ? contract.getGrade()
                : StringUtils.hasText(customer.getGrade()) ? customer.getGrade() : "D";
        Long projectId = contract.getProjectId() == null ? map.getProjectId() : contract.getProjectId();
        String sourceNo = prefix + "V" + (orders.size() + 1);
        MktCommissionService.CommissionEvent event = new MktCommissionService.CommissionEvent(
                MktCommissionService.SOURCE_OUTBOUND, sourceNo, contract.getId(), customer.getId(), seller, grade,
                BigDecimal.ZERO, BigDecimal.ZERO, pool, eventTime, eventTime.plusDays(freezeDays), projectId,
                contract.getWarehouseId(), null, null, null, null);
        MktReferralOrder order = commissionService.createAggregatedOutboundInTransaction(event, count);
        orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getId, order.getId())
                .set(MktReferralOrder::getRemark, "云山发货量汇总订单 " + day + "，" + count + " 票"));
        auditService.log("yunshan.order.generate", "referral_order", order.getId(),
                sourceNo + "：" + day + " 共 " + count + " 票，佣金池 " + pool);
        return outcome;
    }

    /** 该合同当天是否已有“非云山汇总”的已确认出库单且产生了佣金（手工导入或 ERP 推送）。 */
    private boolean hasOtherCommissionOrders(MktServiceContract contract, LocalDate day) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getSourceId, contract.getId())
                .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .notLikeRight(MktReferralOrder::getSourceNo, PREFIX)
                .gt(MktReferralOrder::getPoolAmount, BigDecimal.ZERO)
                .ge(MktReferralOrder::getEventTime, day.atStartOfDay())
                .lt(MktReferralOrder::getEventTime, day.plusDays(1).atStartOfDay()));
        return count != null && count > 0;
    }

    /** 该日期覆盖客户与云仓的服务合同（园区签和云仓直签均按发货量计佣）。 */
    private MktServiceContract resolveContract(MktYunshanVolumeMap map, LocalDate day) {
        MktServiceContract contract = contractMapper.selectOne(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getCustomerId, map.getCustomerId())
                .eq(MktServiceContract::getWarehouseId, map.getWarehouseId())
                .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE,
                        MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING,
                        MktServiceContractService.ST_EXPIRED)
                .and(w -> w.isNull(MktServiceContract::getStartDate).or().le(MktServiceContract::getStartDate, day))
                .and(w -> w.isNull(MktServiceContract::getEndDate).or().ge(MktServiceContract::getEndDate, day))
                .orderByDesc(MktServiceContract::getId).last("limit 1"));
        if (contract == null) return null;
        contract = termsService.atDate(contract, day);
        // 园区签(1)和云仓直签(2)都按发货量计佣；其他签约方式不计佣。
        return Integer.valueOf(1).equals(contract.getSignMode()) || Integer.valueOf(2).equals(contract.getSignMode()) ? contract : null;
    }
}
