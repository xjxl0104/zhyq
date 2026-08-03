package com.zhyq.park.receivable.service;

import com.zhyq.park.importing.service.SpreadsheetCellReader;
import com.zhyq.park.receivable.model.ReceivableWorkbookData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReceivableWorkbookParser {
    public static final List<String> HEADERS = List.of(
            "序号", "协议编号", "租户", "楼层", "计租总面积/方", "其中:实际房产面积", "其中:分摊面积",
            "合同年限", "合同租金总金额", "签约期限", "递增年限及幅度（租金+物业）", "免租期",
            "免租期限", "优惠期/备注", "租金", "物业管理费", "月租金/元", "月物业费/元",
            "月租金物业总计", "租金保证金", "物业保证金", "收款时间", "开始收取租金时间",
            "租金收款账户", "物业管理、水电收款账户", "备注", "保证金差额"
    );

    private static final List<String> KEYS = List.of(
            "seqNo", "agreementNo", "tenantName", "spaceName", "chargeArea", "actualArea", "sharedArea",
            "contractTerm", "contractRentTotal", "contractPeriod", "escalation", "freeTerm", "freePeriod",
            "discount", "rentRate", "propertyRate", "monthlyRent", "monthlyProperty", "monthlyTotal",
            "rentDeposit", "propertyDeposit", "collectionTiming", "firstCollection", "rentAccount",
            "propertyAccount", "notes", "depositDifference"
    );

    private final SpreadsheetCellReader cellReader = new SpreadsheetCellReader();

    public ReceivableWorkbookData parse(byte[] workbookBytes) {
        if (workbookBytes == null || workbookBytes.length == 0) {
            throw new IllegalArgumentException("工作簿不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            Candidate candidate = findCandidate(workbook);
            return parseSheet(candidate);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取应收明细工作簿", e);
        }
    }

    private Candidate findCandidate(Workbook workbook) {
        String mismatch = null;
        for (Sheet sheet : workbook) {
            int lastScanRow = Math.min(19, sheet.getLastRowNum());
            for (int rowIndex = 0; rowIndex <= lastScanRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                int lastCell = Math.max(row.getLastCellNum(), 0);
                for (int col = 0; col < lastCell; col++) {
                    if (!"序号".equals(normalize(text(row.getCell(col))))) {
                        continue;
                    }
                    String validation = validateHeaders(row, col);
                    if (validation == null) {
                        String title = rowIndex > 0 ? blankToNull(text(sheet.getRow(rowIndex - 1), col)) : null;
                        if (!"应收明细登记表".equals(normalize(title))) {
                            mismatch = "表头上一行缺少标题‘应收明细登记表’";
                            continue;
                        }
                        return new Candidate(sheet, rowIndex, col, title);
                    }
                    mismatch = validation;
                }
            }
        }
        throw new IllegalArgumentException(mismatch == null
                ? "前20行未找到应收明细登记表表头"
                : mismatch);
    }

    private String validateHeaders(Row row, int startCol) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < HEADERS.size(); i++) {
            String actual = normalize(text(row.getCell(startCol + i)));
            String expected = normalize(HEADERS.get(i));
            if (!seen.add(actual)) {
                return "应收明细表存在重复表头: " + actual;
            }
            if (!expected.equals(actual)) {
                return "应收明细表第" + (i + 1) + "列表头应为‘" + HEADERS.get(i) + "’，实际为‘" + actual + "’";
            }
        }
        return null;
    }

    private ReceivableWorkbookData parseSheet(Candidate candidate) {
        List<ReceivableWorkbookData.RowData> rows = new ArrayList<>();
        ReceivableWorkbookData.Totals totals = null;
        Sheet sheet = candidate.sheet();
        for (int rowIndex = candidate.headerRow() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String first = normalize(text(row, candidate.startCol()));
            if ("总计".equals(first)) {
                totals = parseTotals(row, candidate.startCol());
                break;
            }
            if (row == null || isBusinessRegionBlank(row, candidate.startCol())) {
                continue;
            }
            rows.add(parseRow(row, candidate.startCol()));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("应收明细登记表没有业务行");
        }
        if (totals == null) {
            throw new IllegalArgumentException("应收明细登记表缺少总计行");
        }
        return new ReceivableWorkbookData(
                candidate.title(), sheet.getSheetName(), candidate.headerRow() + 1,
                List.copyOf(rows), totals);
    }

    private ReceivableWorkbookData.RowData parseRow(Row row, int startCol) {
        Map<String, String> raw = new LinkedHashMap<>();
        Map<String, String> formulas = new LinkedHashMap<>();
        for (int i = 0; i < HEADERS.size(); i++) {
            Cell cell = row.getCell(startCol + i);
            SpreadsheetCellReader.CellValue value = cellReader.read(cell);
            raw.put(KEYS.get(i), value.displayValue());
            if (value.formula() != null) {
                formulas.put(KEYS.get(i), value.formula());
            }
        }
        return new ReceivableWorkbookData.RowData(
                row.getRowNum() + 1,
                integer(row.getCell(startCol)),
                blankToNull(raw.get("agreementNo")),
                blankToNull(raw.get("tenantName")),
                blankToNull(raw.get("spaceName")),
                decimal(row.getCell(startCol + 4)),
                decimal(row.getCell(startCol + 5)),
                decimal(row.getCell(startCol + 6)),
                blankToNull(raw.get("contractTerm")),
                decimal(row.getCell(startCol + 8)),
                blankToNull(raw.get("contractPeriod")),
                blankToNull(raw.get("escalation")),
                blankToNull(raw.get("freeTerm")),
                blankToNull(raw.get("freePeriod")),
                blankToNull(raw.get("discount")),
                blankToNull(raw.get("rentRate")),
                blankToNull(raw.get("propertyRate")),
                decimal(row.getCell(startCol + 16)),
                decimal(row.getCell(startCol + 17)),
                decimal(row.getCell(startCol + 18)),
                decimal(row.getCell(startCol + 19)),
                decimal(row.getCell(startCol + 20)),
                blankToNull(raw.get("collectionTiming")),
                blankToNull(raw.get("firstCollection")),
                blankToNull(raw.get("rentAccount")),
                blankToNull(raw.get("propertyAccount")),
                blankToNull(raw.get("notes")),
                decimal(row.getCell(startCol + 26)),
                Map.copyOf(raw), Map.copyOf(formulas));
    }

    private ReceivableWorkbookData.Totals parseTotals(Row row, int startCol) {
        return new ReceivableWorkbookData.Totals(
                decimal(row.getCell(startCol + 4)),
                decimal(row.getCell(startCol + 5)),
                decimal(row.getCell(startCol + 6)),
                decimal(row.getCell(startCol + 8)),
                decimal(row.getCell(startCol + 16)),
                decimal(row.getCell(startCol + 17)),
                decimal(row.getCell(startCol + 18)),
                decimal(row.getCell(startCol + 19)),
                decimal(row.getCell(startCol + 20)));
    }

    private boolean isBusinessRegionBlank(Row row, int startCol) {
        for (int i = 0; i < HEADERS.size(); i++) {
            if (blankToNull(text(row.getCell(startCol + i))) != null) {
                return false;
            }
        }
        return true;
    }

    private Integer integer(Cell cell) {
        BigDecimal value = decimal(cell);
        if (value == null) {
            throw new IllegalArgumentException("业务行序号不能为空");
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("业务行序号必须是整数: " + value, e);
        }
    }

    private BigDecimal decimal(Cell cell) {
        String value = numericSource(cell);
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("，", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("元", "")
                .replaceAll("\\s+", "");
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(normalized).stripTrailingZeros();
            return parsed.scale() < 0 ? parsed.setScale(0) : parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析数值: " + value, e);
        }
    }

    private String numericSource(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof XSSFCell xssfCell) {
            if (cell.getCellType() == CellType.NUMERIC
                    || (cell.getCellType() == CellType.FORMULA
                    && cell.getCachedFormulaResultType() == CellType.NUMERIC)) {
                return xssfCell.getRawValue();
            }
        }
        return blankToNull(text(cell));
    }

    private String text(Row row, int col) {
        return row == null ? "" : text(row.getCell(col));
    }

    private String text(Cell cell) {
        return cellReader.read(cell).displayValue();
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replace('：', ':')
                .replaceAll("[\\s　]+", "")
                .trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record Candidate(Sheet sheet, int headerRow, int startCol, String title) {}
}
