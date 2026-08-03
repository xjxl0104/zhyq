package com.zhyq.park.vending.service;

import com.zhyq.park.vending.model.VendingImportType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class VendingTemplateService {

    public byte[] template(VendingImportType type) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(type.sheetName());
            var title = sheet.createRow(0);
            title.setHeightInPoints(28);
            var titleCell = title.createCell(0);
            titleCell.setCellValue("智慧园区自动售货机-" + type.sheetName() + "标准导入模板");
            var titleStyle = workbook.createCellStyle();
            var titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, type.headers().size() - 1));

            var header = sheet.createRow(1);
            header.setHeightInPoints(24);
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            for (int i = 0; i < type.headers().size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(type.headers().get(i));
                cell.setCellStyle(headerStyle);
                int width = type.headers().get(i).contains("时间") ? 22 : Math.max(14, type.headers().get(i).length() * 3);
                sheet.setColumnWidth(i, Math.min(width, 40) * 256);
            }
            sheet.createFreezePane(0, 2);
            sheet.setAutoFilter(new CellRangeAddress(1, 1, 0, type.headers().size() - 1));

            var instructions = workbook.createSheet("填写说明");
            String[] lines = {
                    "本模板仅用于智慧园区自动售货机受控导入。",
                    "请勿修改第一张表的标题、表头、列顺序或工作表名称。",
                    "一行只填写一条记录，业务键不得为空且同一文件内不得重复。",
                    "时间格式：yyyy-MM-dd HH:mm:ss；日期格式：yyyy-MM-dd。",
                    "金额填写非负数字，不要带货币符号；数量填写正整数。",
                    "本版本不支持厂商原生导出格式，请先整理为本标准模板。"
            };
            for (int i = 0; i < lines.length; i++) {
                instructions.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            instructions.setColumnWidth(0, 80 * 256);

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成售货机导入模板失败", e);
        }
    }
}
