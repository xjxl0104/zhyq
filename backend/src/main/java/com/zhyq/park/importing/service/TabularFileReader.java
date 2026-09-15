package com.zhyq.park.importing.service;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import com.zhyq.park.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用「表格文件 → 行 × 列文本」读取,供按表头认列的宽松导入复用(中介、供应商等)。
 *
 * <p>支持 Excel(.xlsx/.xls/WPS .et)、CSV/TXT/TSV(逗号或制表符,UTF-8/GBK 自动识别)、Word(.docx 中的表格)。
 * 表头行在前若干行内按「锚点列名」自动探测,兼容表头上方压着标题行的情况;
 * 多 sheet / 多表格时取第一个能找到表头的。</p>
 */
@Slf4j
public final class TabularFileReader {

    public static final String SUPPORTED_HINT = "支持 Excel(.xlsx/.xls)、CSV/TXT、Word(.docx 表格)";
    private static final int HEADER_SCAN_ROWS = 10;

    private TabularFileReader() {}

    /** 读取结果:全部行、表头所在行下标、规范化表头 → 列下标 */
    public record Table(List<List<String>> rows, int headerRow, Map<String, Integer> colIndex) {

        /** 按别名顺序取第一个非空单元格值;都没有返回空串 */
        public String value(List<String> row, List<String> aliases) {
            for (String alias : aliases) {
                Integer idx = colIndex.get(alias);
                if (idx != null && idx < row.size()) {
                    String v = row.get(idx);
                    if (v != null && !v.isBlank()) return v.trim();
                }
            }
            return "";
        }
    }

    /**
     * @param anchorHeaders 表头里至少出现其一的列名(规范化后比对),用于定位表头行
     * @param anchorDesc    找不到表头时提示用户的列名描述,如「中介名称」或「公司名称」
     */
    public static Table read(MultipartFile file, Collection<String> anchorHeaders, String anchorDesc) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        List<List<String>> rows = readRows(file, anchorHeaders);
        int headerRow = findHeaderRow(rows, anchorHeaders);
        if (headerRow < 0) {
            throw new BizException("没找到表头行(应含" + anchorDesc + "等列)");
        }
        Map<String, Integer> colIndex = new HashMap<>();
        List<String> header = rows.get(headerRow);
        for (int i = 0; i < header.size(); i++) {
            colIndex.putIfAbsent(normalize(header.get(i)), i);
        }
        return new Table(rows, headerRow, colIndex);
    }

    /** 表头去空白、星号(必填标记),全角括号转半角 */
    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00a0*＊]", "").replace('（', '(').replace('）', ')');
    }

    // ==================== 各格式读成「行 × 列」 ====================

    private static List<List<String>> readRows(MultipartFile file, Collection<String> anchors) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".tsv")) {
                return readText(bytes);
            }
            if (name.endsWith(".docx")) {
                return readDocx(bytes, anchors);
            }
            if (name.endsWith(".doc") || name.endsWith(".wps")) {
                throw new BizException("老版 Word(.doc)请另存为 .docx 或 Excel 后再导入");
            }
            // 其余(xlsx/xls/et 及未知扩展名)交给 POI 按文件内容识别
            return readWorkbook(bytes, anchors);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[import] 表格文件解析失败 file={}", name, e);
            throw new BizException("文件解析失败," + SUPPORTED_HINT);
        }
    }

    private static List<List<String>> readWorkbook(byte[] bytes, Collection<String> anchors) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes); Workbook wb = WorkbookFactory.create(in)) {
            List<List<String>> first = null;
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                List<List<String>> rows = sheetRows(wb.getSheetAt(s));
                if (first == null && !rows.isEmpty()) first = rows;
                if (findHeaderRow(rows, anchors) >= 0) return rows;
            }
            return first == null ? List.of() : first;
        }
    }

    private static List<List<String>> sheetRows(Sheet sheet) {
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>();
            if (row != null) {
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    cells.add(cellText(row.getCell(c)));
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    private static List<List<String>> readText(byte[] bytes) {
        String text = decode(bytes);
        if (!text.isEmpty() && text.charAt(0) == 0xFEFF) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char sep = firstLine.chars().filter(ch -> ch == '\t').count() > firstLine.chars().filter(ch -> ch == ',').count() ? '\t' : ',';
        CsvReadConfig cfg = CsvReadConfig.defaultConfig();
        cfg.setFieldSeparator(sep);
        CsvData data = new CsvReader(new StringReader(text), cfg).read();
        List<List<String>> rows = new ArrayList<>();
        for (CsvRow row : data.getRows()) {
            rows.add(row.getRawList().stream().map(v -> v == null ? "" : v.trim()).toList());
        }
        return rows;
    }

    /** 严格按 UTF-8 解码,失败则按 GBK(Excel 在中文系统另存的 CSV 多为 GBK) */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static List<List<String>> readDocx(byte[] bytes, Collection<String> anchors) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<List<String>> first = null;
            for (XWPFTable table : doc.getTables()) {
                List<List<String>> rows = new ArrayList<>();
                for (XWPFTableRow tr : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell tc : tr.getTableCells()) {
                        cells.add(tc.getText() == null ? "" : tc.getText().trim());
                    }
                    rows.add(cells);
                }
                if (first == null && !rows.isEmpty()) first = rows;
                if (findHeaderRow(rows, anchors) >= 0) return rows;
            }
            if (first == null) {
                throw new BizException("Word 文档里没有表格,请把内容整理成表格后再导入");
            }
            return first;
        }
    }

    private static int findHeaderRow(List<List<String>> rows, Collection<String> anchors) {
        for (int r = 0; r < Math.min(rows.size(), HEADER_SCAN_ROWS); r++) {
            for (String cell : rows.get(r)) {
                if (anchors.contains(normalize(cell))) {
                    return r;
                }
            }
        }
        return -1;
    }

    static String cellText(Cell c) {
        if (c == null) {
            return "";
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case FORMULA -> {
                try {
                    yield c.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
                }
            }
            default -> "";
        };
    }
}
