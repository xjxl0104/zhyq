package com.zhyq.park.finance.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceOverviewServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void periodReceiptsAndCashReceiptsUseDifferentDatesAndKeepBothSidesOfReversals() {
        Bill octoberBill = bill(1, 1, "2026-10-01", "100", "60");
        octoberBill.setLateFee(new BigDecimal("10"));
        Bill septemberBill = bill(2, 2, "2026-09-01", "200", "80");
        List<Payment> payments = List.of(
                payment(1, "60", "2026-09-30", 0),
                payment(2, "100", "2026-10-02", Payment.VOID_ORIGINAL),
                payment(2, "-100", "2026-10-03", Payment.VOID_REVERSAL),
                payment(2, "80", "2026-10-04", 0),
                payment(2, "50", "2026-09-29", Payment.VOID_ORIGINAL),
                payment(2, "-50", "2026-10-05", Payment.VOID_REVERSAL));

        var result = aggregate(List.of(octoberBill, septemberBill), payments, Map.of());

        assertThat(result.periodReceivable()).isEqualByComparingTo("110");
        assertThat(result.periodReceived()).isEqualByComparingTo("60");
        assertThat(result.periodOutstanding()).isEqualByComparingTo("50");
        assertThat(result.collectionRate()).isEqualByComparingTo("0.5455");
        assertThat(result.cashReceived()).isEqualByComparingTo("30");
        assertThat(result.totalOutstanding()).isEqualByComparingTo("170");
    }

    @Test
    void excludesPayablesDraftsReviewRefundingVoidedAndDeletedBillsAndTheirPayments() {
        Bill effective = bill(1, 1, "2026-10-01", "100", "40");
        Bill settled = bill(2, 1, "2026-10-01", "50", "50");
        settled.setStatus(5);
        List<Bill> bills = new ArrayList<>(List.of(effective, settled));
        for (int status : List.of(1, 2, 7, 8)) {
            Bill excluded = bill(10 + status, status, "2026-10-01", "1000", "500");
            excluded.setStatus(status);
            bills.add(excluded);
        }
        Bill payable = bill(20, 20, "2026-10-01", "1000", "500");
        payable.setDirection(2);
        bills.add(payable);
        Bill deleted = bill(21, 21, "2026-10-01", "1000", "500");
        deleted.setDeleted(1);
        bills.add(deleted);
        Payment deletedPayment = payment(1, "900", "2026-10-01", 0);
        deletedPayment.setDeleted(1);
        List<Payment> payments = new ArrayList<>(bills.stream()
                .map(b -> payment(b.getId(), "10", "2026-10-01", 0)).toList());
        payments.add(deletedPayment);
        payments.add(payment(999, "9999", "2026-10-01", 0));

        var result = aggregate(bills, payments, Map.of());

        assertThat(result.periodReceivable()).isEqualByComparingTo("150");
        assertThat(result.periodReceived()).isEqualByComparingTo("90");
        assertThat(result.totalOutstanding()).isEqualByComparingTo("60");
        assertThat(result.cashReceived()).isEqualByComparingTo("20");
        assertThat(result.topDebtors()).hasSize(1);
    }

    @Test
    void overdueUsesLaterPolicyDateAndIgnoresZeroDebtRegardlessOfStoredStatus() {
        Bill gracePeriod = bill(1, 1, "2026-09-01", "100", "0");
        gracePeriod.setDueDate(LocalDate.of(2026, 9, 1));
        gracePeriod.setReceivableRegisterId(101L);
        gracePeriod.setStatus(6); // 旧状态不能绕过登记表的新政策日。
        Bill overdue = bill(2, 2, "2026-09-01", "200", "20");
        overdue.setDueDate(LocalDate.of(2026, 9, 1));
        overdue.setReceivableRegisterId(102L);
        overdue.setStatus(4); // 部分结清也按日期和余额判断逾期。
        Bill dueToday = bill(3, 3, "2026-10-01", "30", "0");
        dueToday.setDueDate(TODAY);
        Bill future = bill(4, 4, "2026-11-01", "40", "0");
        future.setDueDate(LocalDate.of(2026, 11, 1));
        Bill undated = bill(5, 5, "2026-10-01", "50", "0");
        undated.setDueDate(null);
        Bill zero = bill(6, 6, "2026-09-01", "0", "0");
        zero.setDueDate(LocalDate.of(2026, 9, 1));
        zero.setStatus(6);
        Bill paid = bill(7, 7, "2026-09-01", "10", "10");
        paid.setDueDate(LocalDate.of(2026, 9, 1));
        paid.setStatus(6);

        var result = aggregate(List.of(gracePeriod, overdue, dueToday, future, undated, zero, paid),
                List.of(), Map.of(101L, TODAY, 102L, TODAY.minusDays(1)));

        assertThat(result.totalOutstanding()).isEqualByComparingTo("400");
        assertThat(result.dueOutstanding()).isEqualByComparingTo("310");
        assertThat(result.upcomingOutstanding()).isEqualByComparingTo("40");
        assertThat(result.undatedOutstanding()).isEqualByComparingTo("50");
        assertThat(result.overdueOutstanding()).isEqualByComparingTo("180");
        assertThat(result.overdueBillCount()).isEqualTo(1);
        assertThat(result.topDebtors()).hasSize(5);
    }

    @Test
    void structureUsesSelectedPeriodWhileTrendPadsSixMonthsAndDepositIncludesOtherPeriods() {
        Bill septemberDeposit = bill(1, 1, "2026-09-01", "500", "100");
        septemberDeposit.setFeeType("租金保证金");
        Bill rent = bill(2, 1, "2026-10-01", "200", "50");
        Bill property = bill(3, 1, "2026-10-01", "40", "10");
        property.setFeeType("物业费");
        Bill previousYear = bill(4, 1, "2025-10-01", "800", "200");

        var result = aggregate(List.of(septemberDeposit, rent, property, previousYear), List.of(), Map.of());

        assertThat(result.feeStructure()).extracting(FinanceOverviewService.FeeStructure::feeType)
                .containsExactly("租金", "物业费");
        assertThat(result.feeStructure().get(0).outstanding()).isEqualByComparingTo("150");
        assertThat(result.depositOutstanding()).isEqualByComparingTo("400");
        assertThat(result.trend()).extracting(FinanceOverviewService.MonthTrend::month)
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(result.trend().get(0).receivable()).isZero();
        assertThat(result.trend().get(4).received()).isEqualByComparingTo("100");
        assertThat(result.trend().get(5).receivable()).isEqualByComparingTo("240");
        assertThat(result.totalOutstanding()).isEqualByComparingTo("1180");
    }

    @Test
    void topDebtorsGroupByIdentityInsteadOfNameAndReturnOnlyTopFive() {
        List<Bill> bills = new ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            Bill bill = bill(id, id, "2026-10-01", String.valueOf(id * 100), "0");
            bill.setTenantName("同名公司");
            bills.add(bill);
        }
        bills.add(bill(7, 1, "2026-10-01", "650", "0"));

        var result = aggregate(bills, List.of(), Map.of());

        assertThat(result.topDebtors()).extracting(FinanceOverviewService.Debtor::tenantRefId)
                .containsExactly(1L, 6L, 5L, 4L, 3L);
        assertThat(result.topDebtors().get(0).outstanding()).isEqualByComparingTo("750");
        assertThat(result.topDebtors().get(0).billCount()).isEqualTo(2);
    }

    @Test
    void emptyMonthStillReturnsStableShapeAndCrossYearTrend() {
        var result = FinanceOverviewService.aggregate(YearMonth.of(2027, 2), TODAY,
                List.of(), List.of(), Map.of());

        assertThat(result.month()).isEqualTo("2027-02");
        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.scopeLabel()).isEqualTo("全园区 · 全部项目");
        assertThat(result.periodReceivable()).isZero();
        assertThat(result.collectionRate()).isZero();
        assertThat(result.cashReceived()).isZero();
        assertThat(result.feeStructure()).isEmpty();
        assertThat(result.topDebtors()).isEmpty();
        assertThat(result.trend()).extracting(FinanceOverviewService.MonthTrend::month)
                .containsExactly("2026-09", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02");
    }

    @Test
    void monthInputIsStrictAndDefaultsToBusinessToday() {
        assertThat(FinanceOverviewService.parseMonth(null, TODAY)).isEqualTo(OCTOBER);
        assertThat(FinanceOverviewService.parseMonth("", TODAY)).isEqualTo(OCTOBER);
        assertThat(FinanceOverviewService.parseMonth("2026-01", TODAY)).isEqualTo(YearMonth.of(2026, 1));
        for (String invalid : List.of("2026-1", "2026-13", "2026-00", "0000-01", "2026-10-01", "wrong")) {
            assertThatThrownBy(() -> FinanceOverviewService.parseMonth(invalid, TODAY))
                    .isInstanceOf(BizException.class);
        }
    }

    private static FinanceOverviewService.Overview aggregate(List<Bill> bills, List<Payment> payments,
                                                            Map<Long, LocalDate> policyDates) {
        return FinanceOverviewService.aggregate(OCTOBER, TODAY, bills, payments, policyDates);
    }

    private static Bill bill(long id, long tenantId, String period, String amount, String received) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setCode("TEST-" + id);
        bill.setTenantRefId(tenantId);
        bill.setTenantName("租客 " + tenantId);
        bill.setDirection(1);
        bill.setStatus(3);
        bill.setDeleted(0);
        bill.setFeeType("租金");
        bill.setPeriodStart(LocalDate.parse(period));
        bill.setDueDate(LocalDate.parse(period));
        bill.setAmount(new BigDecimal(amount));
        bill.setPaidAmount(new BigDecimal(received));
        return bill;
    }

    private static Payment payment(long billId, String amount, String day, int voidStatus) {
        Payment payment = new Payment();
        payment.setBillId(billId);
        payment.setAmount(new BigDecimal(amount));
        payment.setPayTime(LocalDate.parse(day).atStartOfDay());
        payment.setVoidStatus(voidStatus);
        payment.setDeleted(0);
        return payment;
    }
}
