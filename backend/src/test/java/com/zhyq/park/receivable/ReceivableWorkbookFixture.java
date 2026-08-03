package com.zhyq.park.receivable;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

final class ReceivableWorkbookFixture {
    private static final String[] HEADERS = {
            "序号", "协议编号", "租户", "楼层", "计租总面积/方", "其中:实际房产面积", "其中:分摊面积",
            "合同年限", "合同租金总金额", "签约期限", "递增年限及幅度\n（租金+物业）", "免租期",
            "免租期限", "优惠期/备注", "租金", "物业管理费", "月租金/元", "月物业费/元",
            "月租金物业总计", "租金保证金", "物业保证金", "收款时间", "开始收取租金时间",
            "租金收款账户", "物业管理、水电收款账户", "备注", "保证金差额"
    };

    private ReceivableWorkbookFixture() {}

    static byte[] build() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(1).createCell(1).setCellValue("应收明细登记表");
            Row header = sheet.createRow(2);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i + 1).setCellValue(HEADERS[i]);
            }

            String[] charge = {"4700", "4700", "9000", "13500", "5000", "6000", "8000", "10000", "23800"};
            String[] actual = {"4000", "4000", "8000", "12000", "4500", "5200", "7000", "8500", "19301.6"};
            String[] shared = {"700", "700", "1000", "1500", "500", "800", "1000", "1500", "4498.4"};
            String[] contractTotal = {"10000000", "10000000", "15000000", "20000000", "12000000", "18000000", "20000000", "25000000", "37139857.679488"};
            String[] rent = {"56400", "60000", "100000", "172800", "100000", "120000", "150000", "180000", "275250"};
            String[] property = {"9447", "9000", "18000", "27000", "10000", "12000", "16000", "20000", "48047"};
            String[] rentDeposit = {"112800", "120000", "200000", "345600", "200000", "240000", "300000", "360000", "834500"};
            String[] propertyDeposit = {"18894", "18000", "36000", "54000", "20000", "24000", "32000", "40000", "58306"};
            String[] escalation = {"每3年-10%", "每3年-8%", "", "每3年递增10%", "", "每年5%", "", "", ""};
            String[] discount = {"20260701-20260930租金按5折", "20260801-20261031抵扣租金", "每年最后一个月免租一个月", "", "", "", "", "", ""};

            for (int i = 0; i < 9; i++) {
                Row row = sheet.createRow(i + 3);
                Object[] values = {
                        i + 1,
                        i == 0 ? null : (i == 1 ? "XY-002/XY-003" : "XY-%03d".formatted(i + 1)),
                        "示例租户" + (i + 1), "示例空间" + (i + 1), charge[i], actual[i], shared[i],
                        "6年", contractTotal[i], "20260501-20320430", escalation[i], i % 2 == 0 ? "2个月" : "",
                        i % 2 == 0 ? "2026.05.01-2026.06.30" : "", discount[i],
                        i == 0 ? "每日每平方含税0.4元" : "每月每平方含税12元",
                        i == 0 ? "每日每平方含税0.067元" : "每月每平方含税2元",
                        rent[i], property[i], Integer.parseInt(rent[i]) + Integer.parseInt(property[i]),
                        rentDeposit[i], propertyDeposit[i], "当月30日前收取下个月租金", "2026年5月1日",
                        "户名：示例企业；开户行：示例银行；账号：622200000001",
                        "户名:示例物业;开户行:示例银行;账号:955880000001",
                        i == 0 ? "历史收款需人工核对" : "", "0"
                };
                for (int col = 0; col < values.length; col++) {
                    set(row.createCell(col + 1), values[col]);
                }
            }

            Row first = sheet.getRow(3);
            first.getCell(17).setCellFormula("0.4*4700*30");
            first.getCell(18).setCellFormula("0.067*4700*30");
            first.getCell(19).setCellFormula("R4+S4");

            Row totals = sheet.createRow(12);
            totals.createCell(1).setCellValue("总计");
            set(totals.createCell(5), "84700");
            set(totals.createCell(6), "72501.6");
            set(totals.createCell(7), "12198.4");
            set(totals.createCell(9), "167139857.679488");
            set(totals.createCell(17), "1214450");
            set(totals.createCell(18), "169494");
            set(totals.createCell(19), "1383944");
            set(totals.createCell(20), "2712900");
            set(totals.createCell(21), "301200");

            sheet.createRow(15).createCell(1).setCellValue("辅助说明，不属于业务数据");
            sheet.createRow(16).createCell(5).setCellValue(999999);
            sheet.createRow(17).createCell(1).setCellValue("结束");

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void set(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
