package com.zhyq.park.receivable.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Service
public class ReceivableExportService {
    public static final List<String> HEADERS = List.of(
            "序号", "协议编号", "租户", "楼层", "计租总面积/方", "其中:实际房产面积", "其中:分摊面积",
            "合同年限", "合同租金总金额", "签约期限", "递增年限及幅度（租金+物业）", "免租期",
            "免租期限", "优惠期/备注", "租金", "物业管理费", "月租金/元", "月物业费/元",
            "月租金物业总计", "租金保证金", "物业保证金", "收款时间", "开始收取租金时间",
            "租金收款账户", "物业管理、水电收款账户", "备注", "保证金差额"
    );

    public byte[] export(List<ReceivableRegister> registers) {
        List<ReceivableRegister> safeRows = registers == null ? List.of() : registers;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("应收明细登记表");
            sheet.createFreezePane(4, 2);

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00####"));

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("应收明细登记表");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.size() - 1));

            Row header = sheet.createRow(1);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 2;
            for (ReceivableRegister register : safeRows) {
                writeRegister(sheet.createRow(rowIndex++), register, decimalStyle);
            }
            writeTotals(sheet.createRow(rowIndex), safeRows, decimalStyle);

            for (int col = 0; col < HEADERS.size(); col++) {
                sheet.autoSizeColumn(col);
                sheet.setColumnWidth(col, Math.min(sheet.getColumnWidth(col) + 512, 48 * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BizException("应收明细导出失败");
        }
    }

    private void writeRegister(Row row, ReceivableRegister value, CellStyle decimalStyle) {
        set(row, 0, value.getSeqNo());
        set(row, 1, value.getAgreementNoRaw());
        set(row, 2, value.getTenantNameRaw());
        set(row, 3, value.getSpaceNameRaw());
        number(row, 4, value.getChargeArea(), decimalStyle);
        number(row, 5, value.getActualArea(), decimalStyle);
        number(row, 6, value.getSharedArea(), decimalStyle);
        set(row, 7, value.getContractTermRaw());
        number(row, 8, value.getContractRentTotal(), decimalStyle);
        set(row, 9, value.getContractPeriodRaw());
        set(row, 10, value.getEscalationRaw());
        set(row, 11, value.getFreeTermRaw());
        set(row, 12, value.getFreePeriodRaw());
        set(row, 13, value.getDiscountRaw());
        set(row, 14, value.getRentRateRaw());
        set(row, 15, value.getPropertyRateRaw());
        number(row, 16, value.getMonthlyRent(), decimalStyle);
        number(row, 17, value.getMonthlyProperty(), decimalStyle);
        number(row, 18, value.getMonthlyTotal(), decimalStyle);
        number(row, 19, value.getRentDeposit(), decimalStyle);
        number(row, 20, value.getPropertyDeposit(), decimalStyle);
        set(row, 21, value.getCollectionTimingRaw());
        set(row, 22, value.getFirstCollectionRaw());
        set(row, 23, value.getRentAccountMasked());
        set(row, 24, value.getPropertyAccountMasked());
        set(row, 25, value.getNotesRaw());
        number(row, 26, value.getDepositDifference(), decimalStyle);
    }

    private void writeTotals(Row row, List<ReceivableRegister> values, CellStyle style) {
        set(row, 0, "总计");
        number(row, 4, sum(values, ReceivableRegister::getChargeArea), style);
        number(row, 5, sum(values, ReceivableRegister::getActualArea), style);
        number(row, 6, sum(values, ReceivableRegister::getSharedArea), style);
        number(row, 8, sum(values, ReceivableRegister::getContractRentTotal), style);
        number(row, 16, sum(values, ReceivableRegister::getMonthlyRent), style);
        number(row, 17, sum(values, ReceivableRegister::getMonthlyProperty), style);
        number(row, 18, sum(values, ReceivableRegister::getMonthlyTotal), style);
        number(row, 19, sum(values, ReceivableRegister::getRentDeposit), style);
        number(row, 20, sum(values, ReceivableRegister::getPropertyDeposit), style);
        number(row, 26, sum(values, ReceivableRegister::getDepositDifference), style);
    }

    private static BigDecimal sum(List<ReceivableRegister> values,
                                  Function<ReceivableRegister, BigDecimal> getter) {
        return values.stream().map(getter).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void set(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) return;
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value.toString());
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }
}
