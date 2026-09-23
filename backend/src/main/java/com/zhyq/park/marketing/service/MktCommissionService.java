package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.engine.CommissionEngine;
import com.zhyq.park.marketing.engine.LadderResolver;
import com.zhyq.park.marketing.engine.Split;
import com.zhyq.park.marketing.engine.SplitRequest;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktSettleBatch;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 计佣落库(PARK-MKT-001 §2.7 / §2.8):计佣事件 → 级差拆分 → 佣金流水,以及冻结/解冻/作废/扣回/结算的状态流转。
 *
 * <p>状态流转全部条件更新(CLAUDE.md 硬约束):{@code update … where id=? and status in (前态)},影响行数 0 抛业务异常。
 * 幂等:同一 (source_type, source_no) 只生成一次(唯一键 uk_referral_order_source 兜底并发);
 * 同一 (order, promoter, sign) 只有一行流水(uk_commission_order_payee)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktCommissionService {

    public static final int SOURCE_LEASE = 1;
    public static final int SOURCE_OUTBOUND = 2;
    public static final int SOURCE_PLATFORM_FEE = 3;
    public static final int SOURCE_CONTRACT_BONUS = 4;

    public static final int ORDER_PENDING = 1;
    public static final int ORDER_CONFIRMED = 2;
    public static final int ORDER_REFUNDED = 3;
    public static final int ORDER_CANCELLED = 4;

    public static final int C_FROZEN = 1;
    public static final int C_SETTLEABLE = 2;
    public static final int C_SETTLED = 3;
    public static final int C_WITHDRAWN = 4;
    public static final int C_VOID = 5;

    private static final int SIGN_POSITIVE = 1;
    private static final int SIGN_CLAWBACK = -1;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter BATCH_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MktReferralOrderMapper orderMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktSettleBatchMapper batchMapper;
    private final MktPromoterMapper promoterMapper;
    private final LadderResolver ladderResolver;
    private final MktAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** 计佣事件的输入。poolFactor:租赁 = 佣金月数;入仓/直签 = 总比例%。 */
    public record CommissionEvent(int sourceType, String sourceNo, Long sourceId, Long customerId,
                                  Long sellerPromoterId, String grade, BigDecimal poolFactor,
                                  BigDecimal baseAmount, BigDecimal poolAmount, LocalDateTime eventTime,
                                  LocalDateTime unfreezeAt, Long projectId, Long warehouseId,
                                  Integer qty, Integer packages, BigDecimal goodsAmount, String logisticsNo) {
        public CommissionEvent(int sourceType, String sourceNo, Long sourceId, Long customerId,
                Long sellerPromoterId, String grade, BigDecimal poolFactor, BigDecimal baseAmount,
                BigDecimal poolAmount, LocalDateTime eventTime, LocalDateTime unfreezeAt, Long projectId, Long warehouseId) {
            this(sourceType, sourceNo, sourceId, customerId, sellerPromoterId, grade, poolFactor, baseAmount,
                    poolAmount, eventTime, unfreezeAt, projectId, warehouseId, null, null, null, null);
        }
    }

    /**
     * 一个事务:写 crm_referral_order(已确认)+ 若干 crm_promoter_commission(冻结)。
     * 已存在同 (source_type, source_no) 的订单直接返回已有记录,不重复生成。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MktReferralOrder createAndSplit(CommissionEvent ev) { return createInternal(ev); }

    /** Use this entry point from a contract/payment transaction so all records commit together. */
    @Transactional
    public MktReferralOrder createAndSplitInTransaction(CommissionEvent ev) { return createInternal(ev); }

    private MktReferralOrder createInternal(CommissionEvent ev) {
        if (ev == null || ev.sourceNo() == null || ev.sourceNo().isBlank() || ev.sourceNo().length() > 64
                || ev.baseAmount() == null || ev.baseAmount().signum() < 0 || ev.poolAmount() == null
                || ev.poolAmount().signum() < 0 || ev.poolFactor() == null || ev.poolFactor().signum() < 0)
            throw new BizException("计佣事件参数或金额无效");
        MktReferralOrder existing = findOrder(ev.sourceType(), ev.sourceNo());
        if (existing != null) {
            log.info("[mkt] 计佣事件已存在,跳过: type={} no={}", ev.sourceType(), ev.sourceNo());
            return existing;
        }
        MktPromoter seller = promoterMapper.selectById(ev.sellerPromoterId());
        if (seller == null) {
            throw new BizException("成交伙伴不存在: " + ev.sellerPromoterId());
        }
        MktReferralOrder order = newOrder(ev);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException dup) {
            return findOrder(ev.sourceType(), ev.sourceNo());
        }
        List<Split> splits = CommissionEngine.split(new SplitRequest(
                ev.poolAmount(), ladderResolver.chainOf(seller), ladderResolver.resolve(seller)));
        for (Split s : splits) {
            commissionMapper.insert(newCommission(order, ev, s));
        }
        auditService.log("commission.create", "referral_order", order.getId(),
                "生成 " + splits.size() + " 行佣金,池 " + ev.poolAmount());
        return order;
    }

    /** 冻结 → 可结算(路径 A:到账事件触发)。返回解冻行数。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int unfreezeByOrder(Long referralOrderId) { return unfreezeOrder(referralOrderId); }

    @Transactional
    public int unfreezeByOrderInTransaction(Long referralOrderId) { return unfreezeOrder(referralOrderId); }

    private int unfreezeOrder(Long referralOrderId) {
        List<MktPromoterCommission> frozen = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getReferralOrderId, referralOrderId)
                .eq(MktPromoterCommission::getStatus, C_FROZEN));
        int n = 0;
        for (MktPromoterCommission c : frozen) {
            n += unfreezeOne(c);
        }
        return n;
    }

    /** 收款红冲且账单已无实收时,可结算正向流水回冻;已结算/已提现流水不逆向改账。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refreezeByOrder(Long referralOrderId) {
        List<MktPromoterCommission> settleable = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getReferralOrderId, referralOrderId)
                .eq(MktPromoterCommission::getStatus, C_SETTLEABLE)
                .eq(MktPromoterCommission::getSign, SIGN_POSITIVE));
        int n = 0;
        for (MktPromoterCommission c : settleable) {
            int updated = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                    .eq(MktPromoterCommission::getId, c.getId())
                    .eq(MktPromoterCommission::getStatus, C_SETTLEABLE)
                    .eq(MktPromoterCommission::getSign, SIGN_POSITIVE)
                    .set(MktPromoterCommission::getStatus, C_FROZEN));
            n += updated;
        }
        return n;
    }

    /** 冻结 → 可结算(路径 B:UnfreezeJob 按 unfreeze_at 触发,订单未退款/取消)。 */
    @Transactional
    public int unfreezeDue(LocalDateTime now) {
        List<MktPromoterCommission> due = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getStatus, C_FROZEN)
                .isNotNull(MktPromoterCommission::getUnfreezeAt)
                .le(MktPromoterCommission::getUnfreezeAt, now));
        int n = 0;
        for (MktPromoterCommission c : due) {
            MktReferralOrder o = orderMapper.selectById(c.getReferralOrderId());
            if (o == null || o.getStatus() != ORDER_CONFIRMED) {
                continue;
            }
            n += unfreezeOne(c);
        }
        return n;
    }

    /**
     * 扣回(§2.8):未结算(冻结/可结算)的流水直接作废;已结算/已提现的生成一条负向流水(sign=-1,可结算),
     * 从伙伴可提现余额里抵扣。同一订单只扣回一次(uk_commission_order_payee 的 sign=-1 行)。
     */
    @Transactional
    public void clawback(Long referralOrderId, String reason) {
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getReferralOrderId, referralOrderId)
                .eq(MktPromoterCommission::getSign, SIGN_POSITIVE));
        // Match withdrawal/account lock order. A locking re-read is required under MySQL
        // REPEATABLE READ so a just-committed withdrawal cannot be missed by the first snapshot.
        rows.stream().map(MktPromoterCommission::getPromoterId).filter(java.util.Objects::nonNull)
                .distinct().sorted().forEach(promoterMapper::selectForUpdate);
        rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getReferralOrderId, referralOrderId)
                .eq(MktPromoterCommission::getSign, SIGN_POSITIVE).orderByAsc(MktPromoterCommission::getId).last("FOR UPDATE"));
        for (MktPromoterCommission c : rows) {
            if (c.getWithdrawalId() != null && c.getStatus() != C_WITHDRAWN)
                throw new BizException("该订单佣金已有待打款提现申请,请先驳回提现再退款/作废");
            if (c.getStatus() == C_FROZEN || c.getStatus() == C_SETTLEABLE) {
                voidOne(c, reason);
            } else if (c.getStatus() == C_SETTLED || c.getStatus() == C_WITHDRAWN) {
                insertClawbackRow(c, reason);
            }
        }
        auditService.log("commission.clawback", "referral_order", referralOrderId, reason);
    }

    /** 按来源(合同 id)找到计佣订单后扣回,合同终止/退租时用。 */
    @Transactional
    public void clawbackBySource(int sourceType, Long sourceId, String reason) {
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, sourceType)
                .eq(MktReferralOrder::getSourceId, sourceId));
        for (MktReferralOrder o : orders) {
            clawback(o.getId(), reason);
        }
    }

    /** 运营作废单条(冻结/可结算 → 作废,写原因)。 */
    @Transactional
    public void voidCommission(Long commissionId, String reason) {
        MktPromoterCommission c = commissionMapper.selectById(commissionId);
        if (c == null) {
            throw new BizException("佣金流水不存在: " + commissionId);
        }
        if (!Integer.valueOf(SIGN_POSITIVE).equals(c.getSign())) {
            throw new BizException("扣回流水不可作废结算");
        }
        voidOne(c, reason);
        auditService.log("commission.void", "commission", commissionId, reason);
    }

    /** 批量结算:可结算 → 已结算,生成结算批次。返回批次号。 */
    @Transactional
    public String settle(List<Long> commissionIds, String operator) {
        if (commissionIds == null || commissionIds.isEmpty()) {
            throw new BizException("没有选择要结算的流水");
        }
        MktSettleBatch batch = new MktSettleBatch();
        batch.setBatchNo("SB-" + LocalDateTime.now().format(BATCH_DAY) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        batch.setOperator(operator);
        batch.setCnt(0);
        batch.setTotalAmount(BigDecimal.ZERO);
        batchMapper.insert(batch);

        BigDecimal total = BigDecimal.ZERO;
        int cnt = 0;
        for (Long id : new java.util.LinkedHashSet<>(commissionIds)) {
            MktPromoterCommission c = commissionMapper.selectById(id);
            if (c == null) {
                throw new BizException("佣金流水不存在: " + id);
            }
            if (!Integer.valueOf(SIGN_POSITIVE).equals(c.getSign())) {
                throw new BizException("扣回流水不可结算");
            }
            int updated = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                    .eq(MktPromoterCommission::getId, id)
                    .eq(MktPromoterCommission::getStatus, C_SETTLEABLE)
                    .isNull(MktPromoterCommission::getWithdrawalId)
                    .eq(MktPromoterCommission::getSign, SIGN_POSITIVE)
                    .set(MktPromoterCommission::getStatus, C_SETTLED)
                    .set(MktPromoterCommission::getSettleBatchId, batch.getId()));
            if (updated == 0) {
                throw new BizException("流水 " + id + " 不是可结算状态,请刷新后重试");
            }
            total = total.add(c.getAmount());
            cnt++;
        }
        batch.setCnt(cnt);
        batch.setTotalAmount(total);
        batchMapper.updateById(batch);
        auditService.log("commission.settle", "settle_batch", batch.getId(), "结算 " + cnt + " 行,合计 " + total);
        return batch.getBatchNo();
    }

    /** 合同终止时无条件作废未结算的正向签约奖。 */
    @Transactional
    public void voidUnsettledBySource(int sourceType, Long sourceId, String reason) {
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, sourceType)
                .eq(MktReferralOrder::getSourceId, sourceId));
        for (MktReferralOrder o : orders) {
            List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                    .eq(MktPromoterCommission::getReferralOrderId, o.getId())
                    .eq(MktPromoterCommission::getSign, SIGN_POSITIVE)
                    .in(MktPromoterCommission::getStatus, C_FROZEN, C_SETTLEABLE));
            for (MktPromoterCommission row : rows) voidOne(row, reason);
        }
    }

    // ---------------- 内部 ----------------

    private MktReferralOrder findOrder(int sourceType, String sourceNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, sourceType)
                .eq(MktReferralOrder::getSourceNo, sourceNo)
                .last("limit 1"));
    }

    private static MktReferralOrder newOrder(CommissionEvent ev) {
        MktReferralOrder o = new MktReferralOrder();
        o.setSourceType(ev.sourceType());
        o.setSourceNo(ev.sourceNo());
        o.setSourceId(ev.sourceId());
        o.setCustomerId(ev.customerId());
        o.setPromoterId(ev.sellerPromoterId());
        o.setCustomerGrade(ev.grade());
        o.setPoolFactor(ev.poolFactor());
        o.setBaseAmount(ev.baseAmount());
        if (ev.sourceType() == SOURCE_OUTBOUND) o.setServiceFee(ev.baseAmount());
        o.setQty(ev.qty()); o.setPackages(ev.packages()); o.setGoodsAmount(ev.goodsAmount()); o.setLogisticsNo(ev.logisticsNo());
        o.setPoolAmount(ev.poolAmount());
        o.setBaseMode(ev.sourceType() == SOURCE_LEASE ? 4 : 1);
        o.setStatus(ORDER_CONFIRMED);
        o.setEventTime(ev.eventTime() == null ? LocalDateTime.now() : ev.eventTime());
        o.setConfirmTime(LocalDateTime.now());
        o.setProjectId(ev.projectId());
        o.setWarehouseId(ev.warehouseId());
        return o;
    }

    private static MktPromoterCommission newCommission(MktReferralOrder order, CommissionEvent ev, Split s) {
        MktPromoterCommission c = new MktPromoterCommission();
        c.setReferralOrderId(order.getId());
        c.setPromoterId(s.promoterId());
        c.setPositionCode(s.positionCode());
        c.setSharePct(s.sharePct());
        c.setDiffPct(s.diffPct());
        c.setBaseAmount(ev.baseAmount());
        // 实际比例 = poolFactor × diff / 100:租赁是"月数",入仓是"%",口径见 crm_referral_order.pool_factor 注释
        c.setRate(ev.poolFactor().multiply(BigDecimal.valueOf(s.diffPct())).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        c.setAmount(s.amount());
        c.setSign(SIGN_POSITIVE);
        c.setStatus(C_FROZEN);
        c.setUnfreezeAt(ev.unfreezeAt());
        c.setProjectId(ev.projectId());
        return c;
    }

    private int unfreezeOne(MktPromoterCommission c) {
        int updated = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getId, c.getId())
                .eq(MktPromoterCommission::getStatus, C_FROZEN)
                .set(MktPromoterCommission::getStatus, C_SETTLEABLE));
        if (updated == 1) {
            eventPublisher.publishEvent(new DomainEvent.CommissionUnfrozen(
                    c.getId(), c.getPromoterId(), c.getAmount(), LocalDateTime.now()));
        }
        return updated;
    }

    private void voidOne(MktPromoterCommission c, String reason) {
        if (!Integer.valueOf(SIGN_POSITIVE).equals(c.getSign())) {
            throw new BizException("扣回流水不可作废结算");
        }
        int updated = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getId, c.getId())
                .in(MktPromoterCommission::getStatus, C_FROZEN, C_SETTLEABLE)
                .isNull(MktPromoterCommission::getWithdrawalId)
                .eq(MktPromoterCommission::getSign, SIGN_POSITIVE)
                .set(MktPromoterCommission::getStatus, C_VOID)
                .set(MktPromoterCommission::getVoidReason, reason));
        if (updated == 0) {
            throw new BizException("流水 " + c.getId() + " 当前状态不可作废,请刷新后重试");
        }
    }

    private void insertClawbackRow(MktPromoterCommission src, String reason) {
        MktPromoterCommission back = new MktPromoterCommission();
        back.setReferralOrderId(src.getReferralOrderId());
        back.setPromoterId(src.getPromoterId());
        back.setPositionCode(src.getPositionCode());
        back.setSharePct(src.getSharePct());
        back.setDiffPct(src.getDiffPct());
        back.setBaseAmount(src.getBaseAmount());
        back.setRate(src.getRate());
        back.setAmount(src.getAmount().negate());
        back.setSign(SIGN_CLAWBACK);
        back.setStatus(C_SETTLEABLE);
        back.setVoidReason(reason);
        back.setProjectId(src.getProjectId());
        try {
            commissionMapper.insert(back);
        } catch (DuplicateKeyException dup) {
            log.info("[mkt] 扣回行已存在,跳过: order={} promoter={}", src.getReferralOrderId(), src.getPromoterId());
        }
    }
}
