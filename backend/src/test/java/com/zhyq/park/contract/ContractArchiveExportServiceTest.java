package com.zhyq.park.contract;

import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.service.ContractArchiveExportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractArchiveExportServiceTest {

    @Test
    void exportsArchiveWithReadableContractFields() throws Exception {
        Contract contract = new Contract();
        contract.setCode("HT2026-TEST-001");
        contract.setTenantName("测试租客");
        contract.setContractType(1);
        contract.setStatus(10);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setRentPrice(new BigDecimal("12.50"));
        contract.setRemark("归档合同原件见附件");

        byte[] bytes = new ContractArchiveExportService().export(List.of(contract));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("合同档案");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("合同编号");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("HT2026-TEST-001");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("测试租客");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("已归档");
            assertThat(sheet.getRow(1).getCell(8).getNumericCellValue()).isEqualTo(12.5d);
        }
    }
}
