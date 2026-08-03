package com.zhyq.park.importing;

import com.zhyq.park.importing.service.SpreadsheetCellReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpreadsheetCellReaderTest {

    @Test
    void readsLiteralAndCachedFormulaSeparately() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("租户甲");
            Cell formula = sheet.createRow(1).createCell(0);
            formula.setCellFormula("1+2");
            wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);

            SpreadsheetCellReader reader = new SpreadsheetCellReader();
            assertEquals("租户甲", reader.read(sheet.getRow(0).getCell(0)).displayValue());
            assertNull(reader.read(sheet.getRow(0).getCell(0)).formula());
            assertEquals("1+2", reader.read(formula).formula());
            assertEquals("3", reader.read(formula).displayValue());
        }
    }
}
