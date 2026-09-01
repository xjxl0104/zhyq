package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Flow;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.entity.Receipt;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.FlowMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 收款服务。收款是一个事务:插入支付单 + 更新账单结清状态 + 写收支流水。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final BillMapper billMapper;
    private final FlowMapper flowMapper;
    private final ReceiptMapper receiptMapper;

    // 允许收款的账单状态:待收付(3)、部分结清(4)、逾期(6)
    private static final int ST_UNPAID = 3;
    private static final int ST_PARTIAL = 4;
    private static final int ST_SETTLED = 5;
    private static final int ST_OVERDUE = 6;

    /**
     * 收款。
     * 幂等:幂等键 payNo 由调用方传入(重试/重放时带同一 payNo 即不会重复入账);
     * 未传时服务端用 UUID 生成(此时每次调用都是一笔新收款)。
     * 校验:账单需处于可收款状态,且本次金额不得超过剩余应收(本金 + 滞纳金 - 实收)。
     *
     * @param billId    账单 id
     * @param amount    收款金额
     * @param payMethod 支付方式
     * @param payNo     幂等键(可空)
     * @return 生成(或已存在)的支付单
     */
    @Transactional(rollbackFor = Exception.class)
    public Payment 收款(Long billId, BigDecimal amount, String payMethod, String payNo) {
        // ① 幂等键必须最先查:同 payNo 重放要原样返回首次结果 —— 哪怕首次收款已把
        //    账单收满结清。放在状态校验之后的话,超时重试会撞上"当前账单状态不可收款",
        //    幂等键在唯一需要它的场景(重放)下反而失效
        if (StringUtils.hasText(payNo)) {
            Payment existed = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo).last("limit 1"));
            if (existed != null) {
                // payNo 全局唯一但不隐含账单:拿别的账单用过的 payNo 来收当前账单,
                // 静默返回那笔无关支付单会让调用方误以为本单收款成功
                if (billId != null && existed.getBillId() != null
                        && !existed.getBillId().equals(billId)) {
                    throw new BizException("幂等键 payNo 已用于其它账单,请更换 payNo");
                }
                return existed;
            }
        } else {
            payNo = "SK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }

        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException("账单不存在: " + billId);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("收款金额必须大于0");
        }
        // 金额列是 DECIMAL(14,2):超过两位小数不拒绝的话,MySQL 会在写入时静默四舍五入,
        // 入账金额与请求金额对不上
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BizException("收款金额最多两位小数");
        }
        // 状态校验:已结清/作废/草稿等不可收款
        Integer st = bill.getStatus();
        if (st == null || (st != ST_UNPAID && st != ST_PARTIAL && st != ST_OVERDUE)) {
            throw new BizException("当前账单状态不可收款(仅待收付/部分结清/逾期账单可收款)");
        }
        // 超额校验:本次金额 <= 本金 + 滞纳金 - 实收(与 BillMetrics.outstandingOf 同口径,
        // 滞纳金计入应收,否则算出来的滞纳金永远没有收款通道)
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        BigDecimal billAmount = bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount();
        BigDecimal lateFee = bill.getLateFee() == null ? BigDecimal.ZERO : bill.getLateFee();
        BigDecimal remaining = billAmount.add(lateFee).subtract(paid);
        if (amount.compareTo(remaining) > 0) {
            throw new BizException("收款金额超过剩余应收 " + remaining.stripTrailingZeros().toPlainString() + " 元");
        }

        Payment payment = new Payment();
        payment.setPayNo(payNo);
        payment.setBillId(billId);
        payment.setAmount(amount);
        payment.setPayMethod(payMethod);
        payment.setPayTime(LocalDateTime.now());
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            // 并发下同 payNo 撞唯一键:说明已入账,回查返回,保持幂等
            Payment dup = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>().eq(Payment::getPayNo, payNo).last("limit 1"));
            if (dup != null) {
                return dup;
            }
            throw e;
        }

        // ② 原子更新账单实收:SET paid_amount = paid_amount + ?,避免读-改-写并发丢更新;
        //    带 paid_amount + amount <= amount + late_fee 的条件防并发超收。
        //    结清判定同口径:付满 本金+滞纳金 才算已结清(MySQL 的 SET 从左到右生效,
        //    IF 里的 paid_amount 已是累加后的新值)
        int updated = billMapper.update(null, new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, billId)
                .setSql("paid_amount = paid_amount + " + amount.toPlainString())
                .setSql("status = IF(paid_amount >= amount + late_fee, " + ST_SETTLED + ", " + ST_PARTIAL + ")")
                .apply("paid_amount + {0} <= amount + late_fee", amount));
        if (updated == 0) {
            // 并发下另一笔收款先到,剩余额度已不足 → 回滚本次(支付单一并回滚)
            throw new BizException("收款失败:剩余应收不足(可能存在并发收款),请刷新后重试");
        }

        // ③ 写收支流水(direction=1 收,match_status=1 已匹配)
        Flow flow = new Flow();
        flow.setFlowNo("LS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        flow.setDirection(1);
        flow.setAmount(amount);
        flow.setBillId(billId);
        flow.setPaymentId(payment.getId());
        flow.setMatchStatus(1);
        flow.setFlowTime(LocalDateTime.now());
        flowMapper.insert(flow);

        // ④ 自动生成收据
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("SJ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        receipt.setPaymentId(payment.getId());
        receipt.setBillId(billId);
        receipt.setTenantRefId(bill.getTenantRefId());
        receipt.setAmount(amount);
        receipt.setPayee("system");
        receiptMapper.insert(receipt);

        return payment;
    }
}
