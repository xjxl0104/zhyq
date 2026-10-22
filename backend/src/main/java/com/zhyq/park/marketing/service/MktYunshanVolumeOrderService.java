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
    private final TransactionTemplate transaction;
    private final ReentrantLock lock = new ReentrantLock();

    public MktYunshanVolumeOrderService(MktYunshanVolumeMapMapper mapMapper, MktYunshanDailyVolumeMapper volumeMapper,
                                        MktServiceContractMapper contractMapper, CustomerMapper customerMapper,
                                        MktWarehouseMapper warehouseMapper, MktReferralOrderMapper orderMapper,
                                        MktCommissionService commissionService, MktCustomerPricingService pricingService,
                                        MktContractTermsService termsService, BizSettings bizSettings,
                                        MktAuditService auditService, PlatformTransactionManager transactionManager) {
        this.mapMapper = mapMapper; this.volumeMapper = volumeMapper; this.contractMapper = contractMapper;
        this.customerMapper = customerMapper; this.warehouseMapper = warehouseMapper; this.orderMapper = orderMapper;
        this.commissionService = commissionService; this.pricingService = pricingService; this.termsService = termsService;
        this.bizSettings = bizSettings; this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** onlyMapId / start / end 均可为空；只处理启用中的关联，且不晚于昨天（当天统计次日才生成）。 */
    public Result generate(Long onlyMapId, LocalDate start, LocalDate end) {
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
