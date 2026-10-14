package com.zhyq.park.finance.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.ContractRef;
import com.zhyq.park.finance.service.FinancePaymentImportService.ImportLine;
import com.zhyq.park.finance.service.FinancePaymentImportService.RowResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancePaymentImportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void allocatesOldestPeriodFirstAndSpansBills() {
        List<RowResult> rows = plan(List.of(line(2, "HT-1", "租金", "2026-03-15", "1500")),
                List.of(bill(1, "租金", "2026-02-01", "1000", "0"), bill(2, "租金", "2026-01-01", "1000", "0"),
                        bill(3, "租金", "2026-03-01", "1000", "0")), Set.of());

        RowResult row = rows.get(0);
        assertThat(row.status()).isEqualTo("ok");
        assertThat(row.allocations()).extracting(FinancePaymentImportService.Allocation::billId).containsExactly(2L, 1L);
        assertThat(row.allocations()).extracting(a -> a.amount().toPlainString()).containsExactly("1000", "500");
    }

    @Test
    void laterRowsSeeWhatEarlierRowsAlreadyConsumed() {
        List<RowResult> rows = plan(List.of(line(2, "HT-1", "租金", "2026-03-15", "800"), line(3, "HT-1", "租金", "2026-04-15", "800"),
                        line(4, "HT-1", "租金", "2026-05-15", "100")),
                List.of(bill(1, "租金", "2026-01-01", "1000", "0"), bill(2, "租金", "2026-02-01", "1000", "0")), Set.of());

        assertThat(rows.get(1).allocations()).extracting(a -> a.billId() + ":" + a.amount().toPlainString())
                .containsExactly("1:200", "2:600");
        assertThat(rows.get(2).status()).isEqualTo("ok");
        assertThat(rows.get(2).allocations().get(0).billId()).isEqualTo(2L);
        assertThat(rows.get(2).allocations().get(0).amount()).isEqualByComparingTo("100");
    }

    @Test
    void rejectsAmountAboveCollectibleAndExplainsWhy() {
        RowResult row = plan(List.of(line(2, "HT-1", "租金", "2026-03-15", "2500")),
                List.of(bill(1, "租金", "2026-01-01", "1000", "200"), bill(2, "租金", "2026-02-01", "1000", "0")), Set.of()).get(0);

        assertThat(row.status()).isEqualTo("error");
        assertThat(row.message()).contains("2500").contains("待收合计 1800");
        assertThat(row.allocations()).isEmpty();
    }

    @Test
    void periodColumnRestrictsAllocationToThatMonth() {
        ImportLine withPeriod = new ImportLine(2, "HT-1", "租金", "2026-03-15", "600", null, "2026-02", null);
        RowResult row = plan(List.of(withPeriod),
                List.of(bill(1, "租金", "2026-01-01", "1000", "0"), bill(2, "租金", "2026-02-01", "1000", "0")), Set.of()).get(0);

        assertThat(row.allocations()).extracting(FinancePaymentImportService.Allocation::billId).containsExactly(2L);
        RowResult tooBig = plan(List.of(new ImportLine(2, "HT-1", "租金", "2026-03-15", "1200", null, "2026-02", null)),
                List.of(bill(2, "租金", "2026-02-01", "1000", "0")), Set.of()).get(0);
        assertThat(tooBig.status()).isEqualTo("error");
        assertThat(tooBig.message()).contains("2026-02账期");
    }

    @Test
    void feeTypeMustMatchAndErrorListsAvailableTypes() {
        RowResult row = plan(List.of(line(2, "HT-1", "物业费", "2026-03-15", "100")),
                List.of(bill(1, "租金", "2026-01-01", "1000", "0"), bill(2, "保证金", "2026-01-01", "500", "0")), Set.of()).get(0);

        assertThat(row.status()).isEqualTo("error");
        assertThat(row.message()).contains("没有「物业费」类待收账单").contains("保证金、租金");
    }

    @Test
    void reportsRowLevelInputErrors() {
        List<RowResult> rows = plan(List.of(
                line(2, "", "租金", "2026-03-15", "100"), line(3, "HT-1", "", "2026-03-15", "100"),
                line(4, "HT-1", "租金", "明天", "100"), line(5, "HT-1", "租金", "2026-10-15", "100"),
                line(6, "HT-1", "租金", "2026-03-15", "abc"), line(7, "HT-1", "租金", "2026-03-15", "-5"),
                line(8, "HT-1", "租金", "2026-03-15", "1.234"), line(9, "HT-X", "租金", "2026-03-15", "100"),
                new ImportLine(10, "HT-1", "租金", "2026-03-15", "100", null, "13月", null)),
                List.of(bill(1, "租金", "2026-01-01", "1000", "0")), Set.of());

        assertThat(rows).extracting(RowResult::status).containsOnly("error");
        assertThat(rows).extracting(RowResult::message).satisfiesExactly(
                m -> assertThat(m).contains("合同编号为空"), m -> assertThat(m).contains("费用类型为空"),
                m -> assertThat(m).contains("收款日期无法识别"), m -> assertThat(m).contains("晚于今天"),
                m -> assertThat(m).contains("金额无法识别"), m -> assertThat(m).contains("大于 0"),
                m -> assertThat(m).contains("两位小数"), m -> assertThat(m).contains("没有合同「HT-X」"),
                m -> assertThat(m).contains("账期无法识别"));
    }

    @Test
    void alreadyImportedRowIsSkippedAndDuplicateRowsInOneFileStayDistinct() {
        List<ImportLine> lines = List.of(line(2, "HT-1", "租金", "2026-03-15", "100"), line(3, "HT-1", "租金", "2026-03-15", "100"));
        List<Bill> bills = List.of(bill(1, "租金", "2026-01-01", "1000", "0"));
        List<RowResult> first = plan(lines, bills, Set.of());
        assertThat(first.get(0).rowKey()).hasSize(24).isNotEqualTo(first.get(1).rowKey());

        List<RowResult> rerun = plan(lines, bills, Set.of(first.get(0).rowKey()));
        assertThat(rerun.get(0).status()).isEqualTo("skipped");
        assertThat(rerun.get(1).status()).isEqualTo("ok");
        assertThat(first.get(0).rowKey()).isEqualTo(rerun.get(0).rowKey());
    }

    @Test
    void settledBillsAreNotCollectibleAndLateFeeCounts() {
        Bill settled = bill(1, "租金", "2026-01-01", "1000", "1000");
        Bill withLateFee = bill(2, "租金", "2026-02-01", "1000", "1000");
        withLateFee.setLateFee(new BigDecimal("30"));

        RowResult row = plan(List.of(line(2, "HT-1", "租金", "2026-03-15", "30")), List.of(settled, withLateFee), Set.of()).get(0);

        assertThat(row.status()).isEqualTo("ok");
        assertThat(row.allocations()).extracting(FinancePaymentImportService.Allocation::billId).containsExactly(2L);
    }

    @Test
    void parsesCsvWithAliasesBomAndIgnoresBlankRows() {
        String csv = "﻿合同号,款项,到账日期,实收金额,账期\nHT-1,租金,2026/3/5,\"1,000.50\",2026-03\n,,,,\nHT-2,物业费,20260306,200,\n";
        List<ImportLine> lines = FinancePaymentImportService.parse(new MockMultipartFile("file", "x.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).rowNo()).isEqualTo(2);
        assertThat(FinancePaymentImportService.parseDate(lines.get(0).payDate())).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(FinancePaymentImportService.parseAmount(lines.get(0).amount())).isEqualByComparingTo("1000.50");
        assertThat(FinancePaymentImportService.parseDate(lines.get(1).payDate())).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(lines.get(1).rowNo()).isEqualTo(4);
    }

    @Test
    void rejectsFilesWithoutRequiredHeaderOrData() {
        assertThatThrownBy(() -> FinancePaymentImportService.parse(csv("名称,数量\n甲,1")))
                .isInstanceOf(BizException.class).hasMessageContaining("没找到表头行");
        assertThatThrownBy(() -> FinancePaymentImportService.parse(csv("合同编号,金额\nHT-1,100")))
                .isInstanceOf(BizException.class).hasMessageContaining("费用类型");
        assertThatThrownBy(() -> FinancePaymentImportService.parse(csv("合同编号,费用类型,收款日期,金额\n")))
                .isInstanceOf(BizException.class).hasMessageContaining("没有数据行");
        assertThatThrownBy(() -> FinancePaymentImportService.parse(null)).isInstanceOf(BizException.class);
    }

    private static MockMultipartFile csv(String text) {
        return new MockMultipartFile("file", "x.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<RowResult> plan(List<ImportLine> lines, List<Bill> bills, Set<String> imported) {
        ContractRef contract = new ContractRef();
        contract.setId(1L);
        contract.setCode("HT-1");
        return FinancePaymentImportService.plan(lines, Map.of("HT-1", contract), Map.of(1L, bills), imported, TODAY);
    }

    private static ImportLine line(int rowNo, String code, String fee, String date, String amount) {
        return new ImportLine(rowNo, code, fee, date, amount, null, null, null);
    }

    private static Bill bill(long id, String fee, String period, String amount, String paid) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setCode("B-" + id);
        bill.setContractId(1L);
        bill.setDirection(1);
        bill.setStatus(paid.equals("0") ? 3 : 4);
        bill.setFeeType(fee);
        bill.setPeriodStart(LocalDate.parse(period));
        bill.setDueDate(LocalDate.parse(period));
        bill.setAmount(new BigDecimal(amount));
        bill.setPaidAmount(new BigDecimal(paid));
        return bill;
    }
}
