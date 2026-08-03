package com.zhyq.park.importing.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;

import java.util.Locale;

/**
 * Reads the workbook-provided cached value of formula cells. Formula evaluation
 * is intentionally outside this class so an uploaded workbook cannot execute
 * formulas, external links, or user-defined functions on the server.
 */
public final class SpreadsheetCellReader {
    private final DataFormatter formatter;

    public SpreadsheetCellReader() {
        formatter = new DataFormatter(Locale.ROOT);
        formatter.setUseCachedValuesForFormulaCells(true);
    }

    public CellValue read(Cell cell) {
        if (cell == null) {
            return new CellValue("", null);
        }
        String formula = cell.getCellType() == CellType.FORMULA
                ? cell.getCellFormula()
                : null;
        return new CellValue(formatter.formatCellValue(cell), formula);
    }

    public record CellValue(String displayValue, String formula) {}
}
