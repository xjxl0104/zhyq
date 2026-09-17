package com.zhyq.park.contract.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.Contract;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** 导出合同档案；附件本身通过文件中心按合同逐个下载，避免把原件打包后失去权限控制。 */
@Service
public class ContractArchiveExportService {

    private static final List<String> HEADERS = List.of(
            "序号", "合同编号", "租客", "合同类型", "状态", "起租日", "到期日", "退租时间",
            "租金单价(元/㎡/月)", "物业单价(元/㎡/月)", "租赁面积(㎡)", "保证金(元)",
            "付款周期(月)", "来源", "备注"
    );

    public byte[] export(List<Contract> contracts) {
        List<Contract> rows = contracts == null ? List.of() : contracts;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("合同档案");
            sheet.createFreezePane(2, 1);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS.get(column));
                cell.setCellStyle(headerStyle);
            }

            int index = 1;
            for (Contract contract : rows) {
                writeRow(sheet.createRow(index), index, contract, decimalStyle);
                index++;
            }
            for (int column = 0; column < HEADERS.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 42 * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BizException("合同档案导出失败");
        }
    }

    private void writeRow(Row row, int index, Contract contract, CellStyle decimalStyle) {
        text(row, 0, index);
        text(row, 1, contract.getCode());
        text(row, 2, contract.getTenantName());
        text(row, 3, typeText(contract.getContractType()));
        text(row, 4, statusText(contract.getStatus()));
        text(row, 5, contract.getStartDate());
        text(row, 6, contract.getEndDate());
        text(row, 7, contract.getTerminateDate());
        decimal(row, 8, contract.getRentPrice(), decimalStyle);
        decimal(row, 9, contract.getPropertyPrice(), decimalStyle);
        decimal(row, 10, contract.getRentArea(), decimalStyle);
        decimal(row, 11, contract.getDeposit(), decimalStyle);
        text(row, 12, contract.getPayCycle());
        text(row, 13, contract.getSource());
        text(row, 14, contract.getRemark());
    }

    private static void text(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.toString());
        }
    }

    private static void decimal(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
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

    private static String typeText(Integer type) {
        return switch (type == null ? 0 : type) {
            case 1 -> "正式合同";
            case 2 -> "意向合同";
            case 3 -> "草稿合同";
            case 4 -> "电子合同";
            case 5 -> "优惠合同";
            case 6 -> "成本合同";
            default -> "-";
        };
    }

    private static String statusText(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "草稿";
            case 2 -> "待审核";
            case 3 -> "待签署";
            case 4 -> "待执行";
            case 5 -> "在租中";
            case 6 -> "变更中";
            case 7 -> "退租中";
            case 8 -> "已到期";
            case 9 -> "已终止";
            case 10 -> "已归档";
            default -> "-";
        };
    }
}
