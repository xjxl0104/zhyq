package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 提现(PARK-MKT-001 §2.9):
 * 可提现余额 = 已结算(3)正向流水 − 可结算(2)负向扣回;余额为负禁止提现。
 * 申请 → 锁定对应流水(写 withdrawal_id)→ 运营审核 → 财务标记打款(pay_no 幂等,条件更新 2→3)→ 流水 → 已提现。
 * 税:tax_mode=1 个税代扣时 tax = amount × tax_rate(简化口径,预扣率由规则参数给),net = amount − tax。
 */
@Service
@RequiredArgsConstructor
public class MktWithdrawalService {

    public static final int WS_PENDING = 1;
    public static final int WS_APPROVED = 2;
    public static final int WS_PAID = 3;
    public static final int WS_REJECTED = 4;

    private static final String MODULE = "marketing";
    private static final String BIZ_TYPE = "withdrawal";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MktWithdrawalMapper withdrawalMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktPromoterMapper promoterMapper;
    private final BizSettings bizSettings;
    private final MktAuditService auditService;
    private final com.zhyq.park.marketing.mapper.MktPromoterAccountMapper accountMapper;
    private final MktPaymentProofService proofService;

    /** 可提现余额:已结算正向 − 可结算负向(扣回);不含已锁进提现单的行。 */
    public BigDecimal balance(Long promoterId) {
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, promoterId)
                .isNull(MktPromoterCommission::getWithdrawalId)
                .and(w -> w.eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, 1)
                        .or().in(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLEABLE, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, -1)));
        return rows.stream().map(MktPromoterCommission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 申请提现:校验伙伴正常、最低金额、余额;锁定流水(按时间先后凑够金额);算税。 */
    @Transactional
    public MktWithdrawal apply(Long promoterId, BigDecimal amount) {
        MktPromoter p = promoterMapper.selectForUpdate(promoterId);
        if (p == null || p.getStatus() == null || p.getStatus() != MktPromoterService.ST_NORMAL) {
            throw new BizException("伙伴状态异常,不能提现");
        }
        if (!Integer.valueOf(1).equals(p.getIdVerified())) throw new BizException("收款资料尚未通过人工审核,暂不能提现");
        var account = accountMapper.selectOne(new LambdaQueryWrapper<com.zhyq.park.marketing.entity.MktPromoterAccount>()
                .eq(com.zhyq.park.marketing.entity.MktPromoterAccount::getPromoterId, promoterId));
        if (account == null || !Integer.valueOf(1).equals(account.getReviewStatus()) || account.getVerifiedAt() == null
                || !StringUtils.hasText(account.getAccountNoEnc())) throw new BizException("请先提交收款资料并等待园区审核通过");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2)
            throw new BizException("提现金额须大于0且最多两位小数");
        amount = amount.setScale(2);
        BigDecimal min = bizSettings.getDecimal(MODULE, "min_withdraw", new BigDecimal("100"));
        if (amount == null || amount.compareTo(min) < 0) {
            throw new BizException("提现金额不能低于 " + min + " 元");
        }
        BigDecimal balance = balance(promoterId);
        if (balance.compareTo(amount) < 0) {
            throw new BizException("可提现余额不足,当前 " + balance);
        }
        int taxMode = bizSettings.getInt(MODULE, "tax_mode", 1);
        BigDecimal taxRate = taxMode == 1 ? bizSettings.getDecimal(MODULE, "tax_rate", new BigDecimal("0.20")) : BigDecimal.ZERO;
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0)
            throw new BizException("代扣比例配置不合法,请联系财务");
        BigDecimal tax = amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        MktWithdrawal w = new MktWithdrawal();
        w.setWithdrawalNo("WD-" + LocalDate.now().format(DAY) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        w.setPromoterId(promoterId);
        w.setAmount(amount);
        w.setTaxMode(taxMode);
        w.setTaxAmount(tax);
        w.setNetAmount(amount.subtract(tax));
        w.setStatus(WS_PENDING);
        w.setAccountId(account.getId()); w.setAccountName(account.getRealName()); w.setAccountType(account.getAccountType());
        w.setAccountNoEnc(account.getAccountNoEnc()); w.setAccountTail(account.getAccountTail());
        w.setBankName(account.getBankName()); w.setAccountVerifiedAt(account.getVerifiedAt());
        w.setProjectId(p.getProjectId());
        // 只锁定覆盖本次金额的流水，不能把伙伴全部余额吞掉。
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, promoterId)
                .isNull(MktPromoterCommission::getWithdrawalId)
                .and(q -> q.eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, 1)
                        .or().in(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLEABLE, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, -1))
                .orderByAsc(MktPromoterCommission::getId));
        List<MktPromoterCommission> selected = new java.util.ArrayList<>();
        BigDecimal selectedAmount = BigDecimal.ZERO;
        // Every outstanding debit must be deducted before selecting positive commissions.
        for (MktPromoterCommission c : rows) if (Integer.valueOf(-1).equals(c.getSign())) {
            selected.add(c); selectedAmount = selectedAmount.add(c.getAmount());
        }
        for (MktPromoterCommission c : rows) {
            if (Integer.valueOf(-1).equals(c.getSign())) continue;
            if (selectedAmount.compareTo(amount) >= 0) break;
            selected.add(c);
            selectedAmount = selectedAmount.add(c.getAmount());
        }
        if (selectedAmount.compareTo(amount) != 0) {
            throw new BizException("提现金额必须与完整流水金额一致,可选择全部可提现余额");
        }
        withdrawalMapper.insert(w);
        for (MktPromoterCommission c : selected) {
            int locked = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                    .eq(MktPromoterCommission::getId, c.getId()).eq(MktPromoterCommission::getPromoterId, promoterId)
                    .eq(MktPromoterCommission::getStatus, c.getStatus()).eq(MktPromoterCommission::getSign, c.getSign())
                    .isNull(MktPromoterCommission::getWithdrawalId)
                    .set(MktPromoterCommission::getWithdrawalId, w.getId()));
            if (locked != 1) {
                throw new BizException("提现余额已被其它申请锁定,请刷新后重试");
            }
        }
        w.setCommissionIds("[" + selected.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.joining(",")) + "]");
        withdrawalMapper.updateById(w);
        auditService.log("withdrawal.apply", BIZ_TYPE, w.getId(), "税前 " + amount + " 税 " + tax);
        return w;
    }

    @Transactional
    public void approve(Long id, String operator) {
        MktWithdrawal current = withdrawalMapper.selectById(id);
        if (current == null || current.getAccountVerifiedAt() == null || !StringUtils.hasText(current.getAccountNoEnc()))
            throw new BizException("该提现单缺少已审核的收款账户快照,请驳回后重新申请");
        int updated = withdrawalMapper.update(null, new LambdaUpdateWrapper<MktWithdrawal>()
                .eq(MktWithdrawal::getId, id).eq(MktWithdrawal::getStatus, WS_PENDING)
                .set(MktWithdrawal::getStatus, WS_APPROVED)
                .set(MktWithdrawal::getAuditBy, operator).set(MktWithdrawal::getAuditAt, LocalDateTime.now()));
        requireUpdated(updated, id);
        auditService.log("withdrawal.approve", BIZ_TYPE, id, null);
    }

    /** 驳回:流水解锁回可提现。 */
    @Transactional
    public void reject(Long id, String reason, String operator) {
        if (!StringUtils.hasText(reason)) throw new BizException("驳回必须填写原因");
        var current = withdrawalMapper.selectById(id);
        if (current == null) throw new BizException("提现单不存在");
        promoterMapper.selectForUpdate(current.getPromoterId());
        int updated = withdrawalMapper.update(null, new LambdaUpdateWrapper<MktWithdrawal>()
                .eq(MktWithdrawal::getId, id).in(MktWithdrawal::getStatus, WS_PENDING, WS_APPROVED)
                .set(MktWithdrawal::getStatus, WS_REJECTED)
                .set(MktWithdrawal::getRejectReason, reason)
                .set(MktWithdrawal::getAuditBy, operator).set(MktWithdrawal::getAuditAt, LocalDateTime.now()));
        requireUpdated(updated, id);
        commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getWithdrawalId, id).set(MktPromoterCommission::getWithdrawalId, null));
        auditService.log("withdrawal.reject", BIZ_TYPE, id, reason);
    }

    /** 标记打款:pay_no 唯一;同 payNo 重放返回原单;条件更新 2→3;流水 → 已提现。照 PaymentService 写法。 */
    @Transactional
    public MktWithdrawal pay(Long id, String payNo, String operator) {
        throw new BizException("请上传付款凭证后确认线下打款");
    }

    @Transactional
    public MktWithdrawal pay(Long id, String payNo, String payProof, String operator) {
        if (!StringUtils.hasText(payNo) || payNo.trim().length() > 64) throw new BizException("打款流水号须为1–64字");
        payNo = payNo.trim();
        MktWithdrawal current = withdrawalMapper.selectById(id);
        if (current == null) throw new BizException("提现单不存在");
        var promoter = promoterMapper.selectForUpdate(current.getPromoterId());
        current = withdrawalMapper.selectOne(new LambdaQueryWrapper<MktWithdrawal>()
                .eq(MktWithdrawal::getId, id).last("FOR UPDATE"));
        if (current == null) throw new BizException("提现单不存在");
        final MktWithdrawal lockedCurrent = current;
        MktWithdrawal existing = withdrawalMapper.selectOne(new LambdaQueryWrapper<MktWithdrawal>()
                .eq(MktWithdrawal::getPayNo, payNo).last("limit 1"));
        if (existing != null) return samePayment(existing, id, payProof);
        if (promoter == null || !Integer.valueOf(MktPromoterService.ST_NORMAL).equals(promoter.getStatus()))
            throw new BizException("伙伴已冻结或退出,请先解除风险或驳回提现");
        if (!Integer.valueOf(WS_APPROVED).equals(current.getStatus())) throw new BizException("仅审核通过的提现单可登记打款");
        if (current.getAccountVerifiedAt() == null || !StringUtils.hasText(current.getAccountNoEnc()))
            throw new BizException("提现单缺少已审核账户快照");
        proofService.require(payProof, "mkt_withdrawal", id);
        List<MktPromoterCommission> linked = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getWithdrawalId, id).last("FOR UPDATE"));
        BigDecimal linkedAmount = linked.stream().map(MktPromoterCommission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (linked.isEmpty() || linkedAmount.compareTo(current.getAmount()) != 0
                || linked.stream().anyMatch(c -> !lockedCurrent.getPromoterId().equals(c.getPromoterId())
                || !((Integer.valueOf(1).equals(c.getSign()) && Integer.valueOf(MktCommissionService.C_SETTLED).equals(c.getStatus()))
                || (Integer.valueOf(-1).equals(c.getSign()) && (Integer.valueOf(MktCommissionService.C_SETTLEABLE).equals(c.getStatus()) || Integer.valueOf(MktCommissionService.C_SETTLED).equals(c.getStatus()))))))
            throw new BizException("提现流水金额不一致或状态已变化,请刷新后重试");
        int updated;
        try {
            updated = withdrawalMapper.update(null, new LambdaUpdateWrapper<MktWithdrawal>()
                    .eq(MktWithdrawal::getId, id).eq(MktWithdrawal::getStatus, WS_APPROVED)
                    .set(MktWithdrawal::getStatus, WS_PAID).set(MktWithdrawal::getPayNo, payNo)
                    .set(MktWithdrawal::getPayProof, payProof).set(MktWithdrawal::getPayMethod, 1)
                    .set(MktWithdrawal::getPayBy, operator).set(MktWithdrawal::getPayAt, LocalDateTime.now()));
        } catch (DuplicateKeyException dup) {
            MktWithdrawal raced = withdrawalMapper.selectOne(new LambdaQueryWrapper<MktWithdrawal>()
                    .eq(MktWithdrawal::getPayNo, payNo).last("limit 1"));
            if (raced != null) return samePayment(raced, id, payProof);
            throw new BizException("打款凭证号已被占用,请刷新");
        }
        requireUpdated(updated, id);
        int paid = commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getWithdrawalId, id)
                .in(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED, MktCommissionService.C_SETTLEABLE)
                .set(MktPromoterCommission::getStatus, MktCommissionService.C_WITHDRAWN));
        if (paid != linked.size()) throw new BizException("提现流水状态已变化,本次打款登记已回滚");
        auditService.log("withdrawal.pay", BIZ_TYPE, id, payNo);
        return withdrawalMapper.selectById(id);
    }

    private MktWithdrawal samePayment(MktWithdrawal existing, Long id, String proof) {
        if (!existing.getId().equals(id) || !Integer.valueOf(WS_PAID).equals(existing.getStatus())
                || !java.util.Objects.equals(existing.getPayProof(), proof))
            throw new BizException("该流水号已用于其他提现或凭证信息不一致");
        return existing;
    }

    private static void requireUpdated(int updated, Long id) {
        if (updated == 0) throw new BizException("提现单 " + id + " 状态已变化,请刷新后重试");
    }
}
