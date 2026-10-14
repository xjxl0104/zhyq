package com.zhyq.park.finance.service;

import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.ContractRef;
import com.zhyq.park.finance.service.FinanceContractCollectionService.ContractCollection;
import com.zhyq.park.finance.service.FinanceContractCollectionService.ContractRow;
import com.zhyq.park.finance.service.FinanceContractCollectionService.RegisterLink;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceContractCollectionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void attributesBillsToContractsThroughRegisterAndKeepsUnlinkedBillsSeparate() {
        Bill direct = bill(1, 7, "2026-10-01", "100", "100");
        direct.setContractId(1L);
        Bill viaRegister = bill(2, 7, "2026-10-01", "50", "0");
        viaRegister.setReceivableRegisterId(10L);
        Bill unlinkedRegister = bill(3, 8, "2026-10-01", "30", "0");
        unlinkedRegister.setReceivableRegisterId(11L);
        Bill unlinkedTenant = bill(4, 9, "2026-10-01", "20", "0");
        Bill sameNameOtherTenant = bill(5, 10, "2026-10-01", "20", "0");
        sameNameOtherTenant.setTenantName("租客 9");

        ContractCollection result = aggregate(
                List.of(direct, viaRegister, unlinkedRegister, unlinkedTenant, sameNameOtherTenant),
                Map.of(10L, new RegisterLink(1L, null), 11L, new RegisterLink(null, null)),
                List.of(contract(1, "HT-001", 7, 5, 1)));

        ContractRow linked = row(result, "contract:1");
        assertThat(linked.contractCode()).isEqualTo("HT-001");
        assertThat(linked.billCount()).isEqualTo(2);
        assertThat(linked.receivable()).isEqualByComparingTo("150");
        assertThat(linked.received()).isEqualByComparingTo("100");
        assertThat(row(result, "register:11").contractId()).isNull();
        assertThat(row(result, "register:11").tenantName()).isEqualTo("租客 8");
        assertThat(row(result, "tenant:9").receivable()).isEqualByComparingTo("20");
        assertThat(row(result, "tenant:10").receivable()).isEqualByComparingTo("20");
        assertThat(result.contracts()).hasSize(4);
    }

    @Test
    void splitsOutstandingIntoOverdueDueAndNotDueSegmentsThatAddUp() {
        Bill overdue = bill(1, 1, "2026-09-01", "100", "40");
        overdue.setDueDate(LocalDate.parse("2026-09-05"));
        Bill grace = bill(2, 1, "2026-10-01", "80", "0");
        grace.setDueDate(LocalDate.parse("2026-10-10"));
        grace.setReceivableRegisterId(10L);
        Bill future = bill(3, 1, "2026-11-01", "70", "0");
        future.setDueDate(LocalDate.parse("2026-11-05"));
        Bill undated = bill(4, 1, "2026-10-01", "10", "0");
        undated.setDueDate(null);
        for (Bill bill : List.of(overdue, future, undated)) bill.setContractId(1L);

        ContractCollection result = aggregate(List.of(overdue, grace, future, undated),
                Map.of(10L, new RegisterLink(1L, LocalDate.parse("2026-10-20"))),
                List.of(contract(1, "HT-001", 1, 5, 1)));

        ContractRow row = row(result, "contract:1");
        assertThat(row.overdueOutstanding()).isEqualByComparingTo("60");
        assertThat(row.dueOutstanding()).isEqualByComparingTo("80");
        assertThat(row.notDueOutstanding()).isEqualByComparingTo("80");
        assertThat(row.overdueOutstanding().add(row.dueOutstanding()).add(row.notDueOutstanding()))
                .isEqualByComparingTo(row.outstanding());
        assertThat(row.state()).isEqualTo("overdue");
    }

    @Test
    void picksMutuallyExclusiveStatesAndSortsRiskFirstWithUnbilledLast() {
        Bill settled = contractBill(1, 1, "100", "100", "2026-10-01");
        Bill unpaidFuture = contractBill(2, 2, "100", "0", "2026-11-01");
        Bill partial = contractBill(3, 3, "100", "30", "2026-11-01");
        Bill overdueSmall = contractBill(4, 4, "100", "90", "2026-09-01");
        Bill overdueLarge = contractBill(5, 5, "500", "0", "2026-09-01");
        List<ContractRef> contracts = List.of(
                contract(1, "HT-001", 1, 9, 1), contract(2, "HT-002", 2, 5, 1),
                contract(3, "HT-003", 3, 5, 1), contract(4, "HT-004", 4, 5, 1),
                contract(5, "HT-005", 5, 5, 1), contract(6, "HT-006", 6, 1, 1),
                contract(7, "HZ-007", 7, 5, 7));

        ContractCollection result = aggregate(
                List.of(settled, unpaidFuture, partial, overdueSmall, overdueLarge), Map.of(), contracts);

        assertThat(result.contracts()).extracting(ContractRow::contractCode)
                .containsExactly("HT-005", "HT-004", "HT-002", "HT-003", "HT-001", "HT-006");
        assertThat(result.contracts()).extracting(ContractRow::state)
                .containsExactly("overdue", "overdue", "unpaid", "partial", "settled", "unbilled");
        ContractRow unbilled = row(result, "contract:6");
        assertThat(unbilled.contractStatus()).isEqualTo(1);
        assertThat(unbilled.tenantName()).isEqualTo("档案租客 6");
        assertThat(unbilled.billCount()).isZero();
        assertThat(unbilled.receivable()).isEqualByComparingTo("0");
        assertThat(result.summary().contractCount()).isEqualTo(6);
        assertThat(result.summary().overdue()).isEqualTo(2);
        assertThat(result.summary().unpaid()).isEqualTo(1);
        assertThat(result.summary().partial()).isEqualTo(1);
        assertThat(result.summary().settled()).isEqualTo(1);
        assertThat(result.summary().unbilled()).isEqualTo(1);
        assertThat(result.summary().receivable()).isEqualByComparingTo("900");
        assertThat(result.summary().received()).isEqualByComparingTo("220");
        assertThat(result.summary().outstanding()).isEqualByComparingTo("680");
        assertThat(result.summary().overdueOutstanding()).isEqualByComparingTo("510");
    }

    @Test
    void includesCooperationContractsOnlyWhenTheyHaveBills() {
        Bill cooperationBill = contractBill(1, 8, "60", "0", "2026-11-01");

        ContractCollection result = aggregate(List.of(cooperationBill), Map.of(),
                List.of(contract(7, "HZ-007", 7, 5, 7), contract(8, "HZ-008", 8, 5, 7)));

        assertThat(result.contracts()).extracting(ContractRow::contractCode).containsExactly("HZ-008");
    }

    @Test
    void groupsFeesPerContractLargestFirstWithDepositsAndLateFees() {
        Bill rent = contractBill(1, 1, "1000", "600", "2026-11-01");
        Bill rentLate = contractBill(2, 1, "200", "0", "2026-09-01");
        rentLate.setLateFee(new BigDecimal("15"));
        Bill deposit = contractBill(3, 1, "3000", "3000", "2026-10-01");
        deposit.setFeeType("保证金");
        Bill blank = contractBill(4, 1, "5", "0", "2026-11-01");
        blank.setFeeType(" ");

        ContractRow row = row(aggregate(List.of(rent, rentLate, deposit, blank), Map.of(),
                List.of(contract(1, "HT-001", 1, 5, 1))), "contract:1");

        assertThat(row.fees()).extracting(FinanceContractCollectionService.FeeLine::feeType)
                .containsExactly("保证金", "租金", "其他");
        var rentLine = row.fees().get(1);
        assertThat(rentLine.receivable()).isEqualByComparingTo("1215");
        assertThat(rentLine.received()).isEqualByComparingTo("600");
        assertThat(rentLine.outstanding()).isEqualByComparingTo("615");
        assertThat(rentLine.overdueOutstanding()).isEqualByComparingTo("215");
        assertThat(rentLine.billCount()).isEqualTo(2);
    }

    @Test
    void bucketsOutstandingByAgeUsingTheSameOverdueStartAsTheOverview() {
        List<Bill> bills = new ArrayList<>();
        bills.add(dueBill(1, "100", "2026-11-01"));
        Bill undated = dueBill(2, "10", "2026-10-01");
        undated.setDueDate(null);
        bills.add(undated);
        Bill grace = dueBill(3, "20", "2026-10-10");
        grace.setReceivableRegisterId(10L);
        bills.add(grace);
        bills.add(dueBill(4, "30", "2026-10-13"));
        bills.add(dueBill(5, "40", "2026-09-14"));
        bills.add(dueBill(6, "50", "2026-09-13"));
        bills.add(dueBill(7, "60", "2026-07-16"));
        bills.add(dueBill(8, "70", "2026-07-15"));
        bills.add(dueBill(9, "999", "2026-01-01"));
        bills.get(8).setPaidAmount(new BigDecimal("999"));

        ContractCollection result = aggregate(bills,
                Map.of(10L, new RegisterLink(null, LocalDate.parse("2026-10-20"))), List.of());

        assertThat(result.aging()).extracting(FinanceContractCollectionService.AgingBucket::key)
                .containsExactly("notDue", "undated", "grace", "d30", "d90", "d90plus");
        assertThat(result.aging()).extracting(bucket -> bucket.amount().toPlainString())
                .containsExactly("100", "10", "20", "70", "110", "70");
        assertThat(result.aging()).extracting(FinanceContractCollectionService.AgingBucket::billCount)
                .containsExactly(1L, 1L, 1L, 2L, 2L, 1L);
    }

    @Test
    void ignoresPayablesDraftsVoidedAndDeletedBills() {
        Bill effective = contractBill(1, 1, "100", "0", "2026-11-01");
        Bill draft = contractBill(2, 1, "1000", "0", "2026-11-01");
        draft.setStatus(1);
        Bill voided = contractBill(3, 1, "1000", "0", "2026-11-01");
        voided.setStatus(8);
        Bill payable = contractBill(4, 1, "1000", "0", "2026-11-01");
        payable.setDirection(2);
        Bill deleted = contractBill(5, 1, "1000", "0", "2026-11-01");
        deleted.setDeleted(1);

        ContractCollection result = aggregate(List.of(effective, draft, voided, payable, deleted), Map.of(),
                List.of(contract(1, "HT-001", 1, 5, 1)));

        assertThat(row(result, "contract:1").receivable()).isEqualByComparingTo("100");
        assertThat(row(result, "contract:1").billCount()).isEqualTo(1);
        assertThat(result.aging().stream().map(FinanceContractCollectionService.AgingBucket::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
    }

    private static ContractCollection aggregate(List<Bill> bills, Map<Long, RegisterLink> registers,
                                                List<ContractRef> contracts) {
        return FinanceContractCollectionService.aggregate(TODAY, bills, registers, contracts,
                Map.of(6L, "档案租客 6", 7L, "档案租客 7"));
    }

    private static ContractRow row(ContractCollection result, String key) {
        return result.contracts().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    private static ContractRef contract(long id, String code, long tenantId, int status, int type) {
        ContractRef contract = new ContractRef();
        contract.setId(id);
        contract.setCode(code);
        contract.setTenantRefId(tenantId);
        contract.setStatus(status);
        contract.setContractType(type);
        contract.setStartDate(LocalDate.parse("2026-01-01"));
        contract.setEndDate(LocalDate.parse("2026-12-31"));
        contract.setDeleted(0);
        return contract;
    }

    private static Bill contractBill(long id, long contractId, String amount, String received, String due) {
        Bill bill = bill(id, contractId, due, amount, received);
        bill.setContractId(contractId);
        return bill;
    }

    private static Bill dueBill(long id, String amount, String due) {
        return bill(id, 1, due, amount, "0");
    }

    private static Bill bill(long id, long tenantId, String day, String amount, String received) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setCode("TEST-" + id);
        bill.setTenantRefId(tenantId);
        bill.setTenantName("租客 " + tenantId);
        bill.setDirection(1);
        bill.setStatus(3);
        bill.setDeleted(0);
        bill.setFeeType("租金");
        bill.setPeriodStart(LocalDate.parse(day));
        bill.setDueDate(LocalDate.parse(day));
        bill.setAmount(new BigDecimal(amount));
        bill.setPaidAmount(new BigDecimal(received));
        return bill;
    }
}
