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

    /**
     * 零元核销。
     *
     * 免租期/抵扣期的账单应收本来就是 0.00,没有钱可收,却因为「收款金额必须大于0」
     * 永远卡在「待收付」。这里不给 0 元收款开口子(0 元支付单/流水/收据是噪音,
     * 还会把"收了多少笔款"撑虚),而是单开一条核销通道:只推状态 + 留痕。
     *
     * 幂等:已结清的账单重复调用直接返回,不报错 —— 收银员手快点两下不该出红字。
     *
     * @param billId   账单 id
     * @param remark   核销说明(留空按「免租期零元核销」记)
     * @param operator 操作人
     * @return 核销后的账单
     */
    @Transactional(rollbackFor = Exception.class)
    public Bill 零元核销(Long billId, String remark, String operator) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException("账单不存在: " + billId);
        }
        if (bill.getStatus() != null && bill.getStatus() == ST_SETTLED) {
            return bill;
        }
        Integer st = bill.getStatus();
        if (st == null || (st != ST_UNPAID && st != ST_PARTIAL && st != ST_OVERDUE)) {
            throw new BizException("当前账单状态不可核销(仅待收付/部分结清/逾期账单可核销)");
        }
        BigDecimal remaining = remainingOf(bill);
        if (remaining.compareTo(BigDecimal.ZERO) != 0) {
            throw new BizException("该账单还有 " + remaining.stripTrailingZeros().toPlainString()
                    + " 元应收,不能零元核销,请走正常收款");
        }
        String note = StringUtils.hasText(remark) ? remark : "免租期零元核销";
        // 条件更新:并发下账单金额若被改过(不再是 0 应收)就不核销,避免把真有欠款的单推成已结清
        int updated = billMapper.update(null, new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, billId)
                .in(Bill::getStatus, ST_UNPAID, ST_PARTIAL, ST_OVERDUE)
                .apply("amount + late_fee - paid_amount = 0")
                .set(Bill::getStatus, ST_SETTLED)
                .set(Bill::getRemark, cut(appendRemark(bill.getRemark(), note + "(" + operator + ")"), 255)));
        if (updated == 0) {
            throw new BizException("核销失败:账单状态或金额已变化,请刷新后重试");
        }
        return billMapper.selectById(billId);
    }

    /**
     * 撤销收款(红冲)。
     *
     * 收款是一笔事务(支付单 + 账单实收 + 收支流水 + 收据),撤销就要把这四样一起退回来。
     * 走会计红冲而不是物理删除:删掉之后再也查不到「谁在什么时候撤了哪一笔」,
     * 对不上账时无从追。原单打 void_status=1,另生成一张负额红冲单指回原单。
     *
     * @param paymentId 要撤销的支付单 id
     * @param reason    撤销原因(收银员填,如「点错租客」)
     * @param operator  操作人
     * @return 生成的红冲单
     */
    @Transactional(rollbackFor = Exception.class)
    public Payment 撤销收款(Long paymentId, String reason, String operator) {
        Payment origin = paymentMapper.selectById(paymentId);
        if (origin == null) {
            throw new BizException("收款记录不存在: " + paymentId);
        }
        int voidStatus = origin.getVoidStatus() == null ? Payment.VOID_NONE : origin.getVoidStatus();
        if (voidStatus == Payment.VOID_ORIGINAL) {
            throw new BizException("该笔收款已撤销,不能重复撤销");
        }
        if (voidStatus == Payment.VOID_REVERSAL) {
            throw new BizException("这是一张红冲单,不能再撤销");
        }
        BigDecimal amount = origin.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该笔收款金额异常,无法红冲");
        }
        Bill bill = billMapper.selectById(origin.getBillId());
        if (bill == null) {
            throw new BizException("收款关联的账单不存在: " + origin.getBillId());
        }
        // 已开票的先别撤:发票金额与收款脱钩会留下一张对不上的票,财务比丢一笔收款更难收拾
        if (bill.getInvoiceStatus() != null && bill.getInvoiceStatus() == 1) {
            throw new BizException("该账单已开票,请先作废发票再撤销收款");
        }

        // ① 原单打撤销标记,带 void_status=0 守卫:并发下只有一次能成功,不会红冲两遍
        int marked = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, paymentId)
                .eq(Payment::getVoidStatus, Payment.VOID_NONE)
                .set(Payment::getVoidStatus, Payment.VOID_ORIGINAL)
                .set(Payment::getVoidReason, cut(reason, 255))
                .set(Payment::getVoidTime, LocalDateTime.now())
                .set(Payment::getVoidBy, operator));
        if (marked == 0) {
            throw new BizException("撤销失败:该笔收款已被其它操作撤销,请刷新后重试");
        }

        // ② 账单实收回退 + 状态重算。MySQL 的 SET 从左到右生效,IF 里读到的已是回退后的 paid_amount;
        //    退到 0 时按应收日决定回「逾期」还是「待收付」,不能一律回待收付把逾期洗白
        int updated = billMapper.update(null, new LambdaUpdateWrapper<Bill>()
                .eq(Bill::getId, bill.getId())
                .setSql("paid_amount = paid_amount - " + amount.toPlainString())
                .setSql("status = IF(paid_amount >= amount + late_fee, " + ST_SETTLED
                        + ", IF(paid_amount > 0, " + ST_PARTIAL
                        + ", IF(due_date IS NOT NULL AND due_date < CURDATE(), "
                        + ST_OVERDUE + ", " + ST_UNPAID + ")))")
                .apply("paid_amount >= {0}", amount));
        if (updated == 0) {
            throw new BizException("撤销失败:账单实收已不足以回退这笔收款,请核对后重试");
        }

        // ③ 负额红冲单,指回原单
        Payment reversal = new Payment();
        reversal.setPayNo("HC" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        reversal.setBillId(bill.getId());
        reversal.setAmount(amount.negate());
        reversal.setPayMethod(origin.getPayMethod());
        reversal.setVoidStatus(Payment.VOID_REVERSAL);
        reversal.setOriginalPaymentId(origin.getId());
        reversal.setVoidReason(cut(reason, 255));
        reversal.setVoidTime(LocalDateTime.now());
        reversal.setVoidBy(operator);
        reversal.setPayTime(LocalDateTime.now());
        reversal.setOperator(operator);
        reversal.setRemark(cut("红冲原收款 " + origin.getPayNo(), 255));
        paymentMapper.insert(reversal);

        // ④ 负额收支流水:方向仍是「收入」,金额取负 —— 会计红冲口径。
        //    写成 direction=2(支出)的话,园区的支出统计会被退款撑出来,报表就废了
        Flow flow = new Flow();
        flow.setFlowNo("LS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        flow.setDirection(1);
        flow.setAmount(amount.negate());
        flow.setBillId(bill.getId());
        flow.setPaymentId(reversal.getId());
        flow.setMatchStatus(1);
        flow.setFlowTime(LocalDateTime.now());
        flow.setRemark(cut("红冲原收款 " + origin.getPayNo()
                + (StringUtils.hasText(reason) ? " · " + reason : ""), 255));
        flowMapper.insert(flow);

        // ⑤ 作废原收款生成的收据(不删:打过的收据号要能追)
        receiptMapper.update(null, new LambdaUpdateWrapper<Receipt>()
                .eq(Receipt::getPaymentId, origin.getId())
                .set(Receipt::getVoidStatus, 1));

        return reversal;
    }

    /** 剩余应收 = 本金 + 滞纳金 - 实收,与 BillMetrics.outstandingOf 同口径 */
    private static BigDecimal remainingOf(Bill bill) {
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        BigDecimal billAmount = bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount();
        BigDecimal lateFee = bill.getLateFee() == null ? BigDecimal.ZERO : bill.getLateFee();
        return billAmount.add(lateFee).subtract(paid);
    }

    private static String appendRemark(String origin, String addition) {
        return StringUtils.hasText(origin) ? origin + "；" + addition : addition;
    }

    /** VARCHAR(255) 列写超长会被 MySQL 静默截断,这里先截好,别让备注把留痕吃掉 */
    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
