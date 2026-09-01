package com.zhyq.park.finance.service;

import com.zhyq.park.finance.entity.Bill;

import java.math.BigDecimal;

/**
 * 账单金额口径的单一真相源。
 *
 * <p>账单页顶部卡片、财务报表、驾驶舱都在各自遍历 {@code fin_bill} 求和,同一个"实收"
 * 三处各写一遍 —— 其中账单页与报表都漏了收款方向的过滤,把应付账单的已付金额也算进了实收,
 * 只是演示数据里恰好没有应付账单才没露馅。口径写在这里一处,谁要用谁调,不再各算各的。</p>
 *
 * <p>方向:1 收款(应收) 2 付款(应付)。状态见 {@code Bill} 的 javadoc。</p>
 *
 * <p>滞纳金计入应收:应收 = 本金 + 滞纳金。此前滞纳金算得出来却收不进去 ——
 * 收款上限与结清条件只看本金,租客付满本金账单就"已结清",滞纳金永久挂空,
 * 收银台还把这个租客从欠款列表里移走。欠款、结清、剩余可收三处必须与这里同源
 * (PaymentService 的 SQL 条件是同一口径的数据库侧写法)。</p>
 */
public final class BillMetrics {

    /** 方向:收款(应收) */
    public static final int DIRECTION_RECEIVE = 1;
    /** 方向:付款(应付) */
    public static final int DIRECTION_PAY = 2;

    /** 状态:已逾期 */
    public static final int STATUS_OVERDUE = 6;

    private BillMetrics() {}

    /** 是否应收方向。财务的"应收/实收/欠款"三个数都只看这个方向 */
    public static boolean isReceivable(Bill bill) {
        return bill != null && bill.getDirection() != null && bill.getDirection() == DIRECTION_RECEIVE;
    }

    /** 应收金额 = 本金 + 滞纳金:非应收方向记 0,免得调用方忘了判方向 */
    public static BigDecimal receivableOf(Bill bill) {
        return isReceivable(bill) ? nz(bill.getAmount()).add(nz(bill.getLateFee())) : BigDecimal.ZERO;
    }

    /**
     * 实收金额:同样只认应收方向。
     *
     * <p>付款账单的 paid_amount 是"我们付出去的钱",算进"实收"会让收缴率虚高。</p>
     */
    public static BigDecimal receivedOf(Bill bill) {
        return isReceivable(bill) ? nz(bill.getPaidAmount()) : BigDecimal.ZERO;
    }

    /** 未结欠款 = 应收 - 实收,只对应收方向有意义;已收满或超收时返回 0 */
    public static BigDecimal outstandingOf(Bill bill) {
        BigDecimal owe = receivableOf(bill).subtract(receivedOf(bill));
        return owe.signum() > 0 ? owe : BigDecimal.ZERO;
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
