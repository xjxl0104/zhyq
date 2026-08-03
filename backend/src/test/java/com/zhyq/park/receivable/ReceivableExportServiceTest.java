package com.zhyq.park.receivable;

import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.service.ReceivableExportService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableExportServiceTest {
    @Test
    void exportsExactTwentySevenColumnsRowsTotalsAndMaskedAccounts() throws Exception {
        ReceivableRegister first = register(1, "示例租户甲", "100000", "20000");
        ReceivableRegister second = register(2, "示例租户乙", "200000", "30000");

        byte[] bytes = new ReceivableExportService().export(List.of(first, second));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("应收明细登记表", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(27, sheet.getRow(1).getLastCellNum());
            assertEquals("序号", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("保证金差额", sheet.getRow(1).getCell(26).getStringCellValue());
            assertEquals("示例租户甲", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals("总计", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(300000D, sheet.getRow(4).getCell(16).getNumericCellValue());
            String rentAccount = sheet.getRow(2).getCell(23).getStringCellValue();
            assertTrue(rentAccount.contains("****"));
            assertFalse(rentAccount.contains("622200000001"));
            assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("v1:"));
        }
    }

    private static ReceivableRegister register(int seq, String tenant, String rent, String property) {
        ReceivableRegister register = new ReceivableRegister();
        register.setSeqNo(seq);
        register.setAgreementNoRaw(seq == 1 ? null : "XY-002");
        register.setTenantNameRaw(tenant);
        register.setSpaceNameRaw("示例空间" + seq);
        register.setChargeArea(new BigDecimal("1000"));
        register.setActualArea(new BigDecimal("900"));
        register.setSharedArea(new BigDecimal("100"));
        register.setContractTermRaw("6年");
        register.setContractRentTotal(new BigDecimal("10000000"));
        register.setContractPeriodRaw("20260501-20320430");
        register.setRentRateRaw("每月每平方含税10元");
        register.setPropertyRateRaw("每月每平方含税2元");
        register.setMonthlyRent(new BigDecimal(rent));
        register.setMonthlyProperty(new BigDecimal(property));
        register.setMonthlyTotal(new BigDecimal(rent).add(new BigDecimal(property)));
        register.setRentDeposit(new BigDecimal("200000"));
        register.setPropertyDeposit(new BigDecimal("40000"));
        register.setRentAccountMasked("户名：示例；开户行：示例银行；账号：6222****0001");
        register.setPropertyAccountMasked("户名：示例；开户行：示例银行；账号：9558****0001");
        register.setDepositDifference(BigDecimal.ZERO);
        return register;
    }
}
