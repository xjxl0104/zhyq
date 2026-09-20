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

    /** 可提现余额:已结算正向 − 可结算负向(扣回);不含已锁进提现单的行。 */
    public BigDecimal balance(Long promoterId) {
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, promoterId)
                .isNull(MktPromoterCommission::getWithdrawalId)
                .and(w -> w.eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, 1)
                        .or().eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLEABLE).eq(MktPromoterCommission::getSign, -1)));
        return rows.stream().map(MktPromoterCommission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 申请提现:校验伙伴正常、最低金额、余额;锁定流水(按时间先后凑够金额);算税。 */
    @Transactional
    public MktWithdrawal apply(Long promoterId, BigDecimal amount) {
        MktPromoter p = promoterMapper.selectById(promoterId);
        if (p == null || p.getStatus() == null || p.getStatus() != MktPromoterService.ST_NORMAL) {
            throw new BizException("伙伴状态异常,不能提现");
        }
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
        BigDecimal tax = amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        MktWithdrawal w = new MktWithdrawal();
        w.setWithdrawalNo("WD-" + LocalDate.now().format(DAY) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        w.setPromoterId(promoterId);
        w.setAmount(amount);
        w.setTaxMode(taxMode);
        w.setTaxAmount(tax);
        w.setNetAmount(amount.subtract(tax));
        w.setStatus(WS_PENDING);
        w.setProjectId(p.getProjectId());
        withdrawalMapper.insert(w);

        // 锁定流水:全部已结算正向 + 可结算负向都归入本单(扣回必须一起抵掉),记 id 列表
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getPromoterId, promoterId)
                .isNull(MktPromoterCommission::getWithdrawalId)
                .and(q -> q.eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED).eq(MktPromoterCommission::getSign, 1)
                        .or().eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLEABLE).eq(MktPromoterCommission::getSign, -1))
                .orderByAsc(MktPromoterCommission::getId));
        for (MktPromoterCommission c : rows) {
            commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                    .eq(MktPromoterCommission::getId, c.getId()).isNull(MktPromoterCommission::getWithdrawalId)
                    .set(MktPromoterCommission::getWithdrawalId, w.getId()));
        }
        w.setCommissionIds("[" + rows.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.joining(",")) + "]");
        withdrawalMapper.updateById(w);
        auditService.log("withdrawal.apply", BIZ_TYPE, w.getId(), "税前 " + amount + " 税 " + tax);
        return w;
    }

    @Transactional
    public void approve(Long id, String operator) {
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
        if (!StringUtils.hasText(payNo)) throw new BizException("请填写打款凭证号");
        MktWithdrawal existing = withdrawalMapper.selectOne(new LambdaQueryWrapper<MktWithdrawal>()
                .eq(MktWithdrawal::getPayNo, payNo).last("limit 1"));
        if (existing != null) {
            if (!existing.getId().equals(id)) throw new BizException("该凭证号已用于其它提现单");
            return existing;
        }
        int updated;
        try {
            updated = withdrawalMapper.update(null, new LambdaUpdateWrapper<MktWithdrawal>()
                    .eq(MktWithdrawal::getId, id).eq(MktWithdrawal::getStatus, WS_APPROVED)
                    .set(MktWithdrawal::getStatus, WS_PAID)
                    .set(MktWithdrawal::getPayNo, payNo).set(MktWithdrawal::getPayMethod, 1)
                    .set(MktWithdrawal::getPayBy, operator).set(MktWithdrawal::getPayAt, LocalDateTime.now()));
        } catch (DuplicateKeyException dup) {
            return withdrawalMapper.selectOne(new LambdaQueryWrapper<MktWithdrawal>().eq(MktWithdrawal::getPayNo, payNo).last("limit 1"));
        }
        requireUpdated(updated, id);
        commissionMapper.update(null, new LambdaUpdateWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getWithdrawalId, id)
                .in(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLED, MktCommissionService.C_SETTLEABLE)
                .set(MktPromoterCommission::getStatus, MktCommissionService.C_WITHDRAWN));
        auditService.log("withdrawal.pay", BIZ_TYPE, id, payNo);
        return withdrawalMapper.selectById(id);
    }

    private static void requireUpdated(int updated, Long id) {
        if (updated == 0) throw new BizException("提现单 " + id + " 状态已变化,请刷新后重试");
    }
}
