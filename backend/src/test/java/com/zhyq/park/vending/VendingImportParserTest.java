package com.zhyq.park.vending;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.vending.model.VendingImportData;
import com.zhyq.park.vending.model.VendingImportType;
import com.zhyq.park.vending.service.VendingImportParser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendingImportParserTest {
    private final VendingImportParser parser = new VendingImportParser();

    @Test
    void parsesOneValidRowForEachType() throws Exception {
        assertValid(VendingImportType.MACHINE,
                "M-01", "一号机", "A栋一层", "FJ-100", "在线", "2026-08-01 09:30:00");
        assertValid(VendingImportType.SALE,
                "O-01", "1", "M-01", "P-01", "矿泉水", "2", "6.00", "1.00", "5.00",
                "微信", "2026-08-01 10:30:00", "已完成");
        assertValid(VendingImportType.RESTOCK,
                "R-01", "M-01", "P-01", "矿泉水", "10", "张三", "2026-08-01 08:00:00");
        assertValid(VendingImportType.FAULT,
                "F-01", "M-01", "卡货", "2026-08-01 11:00:00", "2026-08-01 11:30:00", "已恢复", "现场处理");
        assertValid(VendingImportType.RECONCILIATION,
                "S-01", "2026-08-01", "2026-08-31", "1000", "20", "50", "930", "已结算");
    }

    @Test
    void reportsRowLevelErrorsWithoutRejectingTheWholeWorkbook() throws Exception {
        VendingImportData machine = parser.parse(VendingImportType.MACHINE,
                workbook(VendingImportType.MACHINE, "", "一号机", "A栋", "FJ", "未知", "2026-08-01 09:30:00"));
        assertFalse(machine.rows().get(0).valid());
        assertContains(machine.rows().get(0).errors(), "机器编号", "运行状态");

        VendingImportData sale = parser.parse(VendingImportType.SALE,
                workbook(VendingImportType.SALE, "", "", "M-01", "P-01", "水", "-1", "6", "0", "7",
                        "微信", "2026-08-01 10:30:00", "未知"));
        assertContains(sale.rows().get(0).errors(), "订单号", "行号", "数量", "实付金额", "订单状态");

        VendingImportData restock = parser.parse(VendingImportType.RESTOCK,
                workbook(VendingImportType.RESTOCK, "R-01", "", "P-01", "水", "-2", "张三", "2026-08-01 08:00:00"));
        assertContains(restock.rows().get(0).errors(), "机器编号", "补货数量");

        VendingImportData fault = parser.parse(VendingImportType.FAULT,
                workbook(VendingImportType.FAULT, "F-01", "M-01", "卡货", "2026-08-02 11:00:00",
                        "2026-08-01 11:00:00", "未知", ""));
        assertContains(fault.rows().get(0).errors(), "恢复时间", "状态");

        VendingImportData settlement = parser.parse(VendingImportType.RECONCILIATION,
                workbook(VendingImportType.RECONCILIATION, "S-01", "2026-08-31", "2026-08-01",
                        "1000", "20", "50", "930", "未知"));
        assertContains(settlement.rows().get(0).errors(), "结算周期", "状态");
    }

    @Test
    void rejectsAliasOrReorderedHeaders() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("销售数据");
            sheet.createRow(0).createCell(0).setCellValue("标题");
            var header = sheet.createRow(1);
            for (int i = 0; i < VendingImportType.SALE.headers().size(); i++) {
                header.createCell(i).setCellValue(i == 0 ? "订单编号" : VendingImportType.SALE.headers().get(i));
            }
            workbook.write(out);
            bytes = out.toByteArray();
        }

        assertThrows(BizException.class, () -> parser.parse(VendingImportType.SALE, bytes));
    }

    private void assertValid(VendingImportType type, String... values) throws Exception {
        VendingImportData data = parser.parse(type, workbook(type, values));
        assertEquals(1, data.rows().size());
        assertTrue(data.rows().get(0).valid(), data.rows().get(0).errors().toString());
    }

    private byte[] workbook(VendingImportType type, String... values) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(type.sheetName());
            sheet.createRow(0).createCell(0).setCellValue("智慧园区自动售货机标准导入模板");
            var header = sheet.createRow(1);
            for (int i = 0; i < type.headers().size(); i++) {
                header.createCell(i).setCellValue(type.headers().get(i));
            }
            var row = sheet.createRow(2);
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void assertContains(List<String> errors, String... fragments) {
        String message = String.join(";", errors);
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), message);
        }
    }
}
