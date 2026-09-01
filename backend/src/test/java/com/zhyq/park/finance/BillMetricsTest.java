package com.zhyq.park.finance;

import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.service.BillMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账单金额口径。
 *
 * <p>账单页顶部卡片与财务报表此前各遍历一遍 fin_bill 求和,两处都漏了收款方向的过滤,
 * 把应付账单已付出去的钱也算进了"实收" —— 演示数据里恰好没有应付账单才没露馅,
 * 一旦有应付账单,收缴率就会虚高。这些用例把口径钉死。</p>
 */
class BillMetricsTest {

    private static Bill bill(Integer direction, String amount, String paid) {
        Bill b = new Bill();
        b.setDirection(direction);
        b.setAmount(amount == null ? null : new BigDecimal(amount));
        b.setPaidAmount(paid == null ? null : new BigDecimal(paid));
        return b;
    }

    @Test
    @DisplayName("应付账单不计入应收与实收 —— 付出去的钱不是收进来的钱")
    void payableBillsExcludedFromBothSides() {
        Bill payable = bill(BillMetrics.DIRECTION_PAY, "50000", "50000");

        assertThat(BillMetrics.isReceivable(payable)).isFalse();
        assertThat(BillMetrics.receivableOf(payable)).isEqualByComparingTo("0");
        assertThat(BillMetrics.receivedOf(payable)).isEqualByComparingTo("0");
        assertThat(BillMetrics.outstandingOf(payable)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("应收账单正常计入,欠款 = 应收 - 实收")
    void receivableBillCountsNormally() {
        Bill b = bill(BillMetrics.DIRECTION_RECEIVE, "124800", "24800");

        assertThat(BillMetrics.receivableOf(b)).isEqualByComparingTo("124800");
        assertThat(BillMetrics.receivedOf(b)).isEqualByComparingTo("24800");
        assertThat(BillMetrics.outstandingOf(b)).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("已收满或超收时欠款记 0,不出现负数欠款")
    void noNegativeOutstanding() {
        assertThat(BillMetrics.outstandingOf(bill(1, "1000", "1000"))).isEqualByComparingTo("0");
        assertThat(BillMetrics.outstandingOf(bill(1, "1000", "1200"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("金额为空按 0 处理,方向为空不算应收")
    void handlesNulls() {
        assertThat(BillMetrics.receivableOf(bill(1, null, null))).isEqualByComparingTo("0");
        assertThat(BillMetrics.receivedOf(bill(1, "100", null))).isEqualByComparingTo("0");
        assertThat(BillMetrics.isReceivable(bill(null, "100", "0"))).isFalse();
        assertThat(BillMetrics.isReceivable(null)).isFalse();
    }

    // ---------- 滞纳金口径:计入应收 ----------
    // 此前滞纳金算得出来却收不进去:收款上限与结清条件只看 amount,租客付满本金
    // 账单就"已结清",滞纳金永久挂空,收银台还把这个租客从欠款列表里移走。
    // 现在口径统一为:应收 = 本金 + 滞纳金,欠款、结清、可收上限全部同源。

    private static Bill lateBill(String amount, String paid, String lateFee) {
        Bill b = bill(BillMetrics.DIRECTION_RECEIVE, amount, paid);
        b.setLateFee(lateFee == null ? null : new BigDecimal(lateFee));
        return b;
    }

    @Test
    @DisplayName("应收含滞纳金:本金付清但滞纳金没付,欠款不是 0,账单不算收齐")
    void lateFeeCountsIntoReceivableAndOutstanding() {
        Bill b = lateBill("1000", "1000", "5.00");

        assertThat(BillMetrics.receivableOf(b)).isEqualByComparingTo("1005.00");
        assertThat(BillMetrics.outstandingOf(b)).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("本金 + 滞纳金一起付清后欠款归 0;滞纳金为空按 0 处理")
    void settledIncludingLateFee() {
        assertThat(BillMetrics.outstandingOf(lateBill("1000", "1005", "5.00"))).isEqualByComparingTo("0");
        assertThat(BillMetrics.receivableOf(lateBill("1000", "0", null))).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("应付账单的滞纳金同样不计入应收口径")
    void payableLateFeeStillExcluded() {
        Bill payable = bill(BillMetrics.DIRECTION_PAY, "50000", "0");
        payable.setLateFee(new BigDecimal("100"));

        assertThat(BillMetrics.receivableOf(payable)).isEqualByComparingTo("0");
        assertThat(BillMetrics.outstandingOf(payable)).isEqualByComparingTo("0");
    }
}
