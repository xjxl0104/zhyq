package com.zhyq.park.crm.service;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.mapper.ChannelMapper;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 中介登记表导入。支持 Excel(.xlsx/.xls/WPS .et)、CSV/TXT(逗号或制表符,UTF-8/GBK 自动识别)、Word(.docx 里的表格)。
 *
 * <p>各格式先统一读成「行 × 列」文本,再按表头文字认列:每个字段允许多个常见叫法,
 * 列顺序随意、多余列忽略。表头行在前 10 行内自动探测(含「中介名称」等名称列的那一行)。</p>
 *
 * <p>去重:同名称(+电话)已存在则跳过,重复导入同一份文件不会翻倍。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelImportService {

    private static final int HEADER_SCAN_ROWS = 10;

    /** 名称列的叫法,同时用来定位表头行 */
    private static final List<String> NAME_HEADERS = List.of(
            "中介名称", "中介公司", "中介公司名称", "公司名称", "机构名称", "渠道名称", "经纪人", "经纪人姓名", "名称");

    /** 字段 → 表头叫法(去空格后比对) */
    private static final Map<List<String>, BiConsumer<Channel, String>> COLUMN_MAP = new java.util.LinkedHashMap<>();

    static {
        COLUMN_MAP.put(NAME_HEADERS, Channel::setName);
        COLUMN_MAP.put(List.of("中介类型", "类型", "机构类型"), Channel::setAgencyType);
        COLUMN_MAP.put(List.of("联系人", "联系人姓名", "对接人"), Channel::setContact);
        COLUMN_MAP.put(List.of("联系电话", "电话", "手机", "手机号", "联系方式"), Channel::setPhone);
        COLUMN_MAP.put(List.of("微信", "微信号"), Channel::setWechat);
        COLUMN_MAP.put(List.of("所在地区", "地区", "区域", "覆盖区域"), Channel::setRegion);
        COLUMN_MAP.put(List.of("办公地址", "地址", "公司地址"), Channel::setAddress);
        COLUMN_MAP.put(List.of("擅长业务/资源", "擅长业务", "客户资源", "资源方向", "主营业务", "业务范围"), Channel::setResourceDesc);
        COLUMN_MAP.put(List.of("合作等级", "等级"), (c, v) -> c.setGrade(normalizeGrade(v)));
        COLUMN_MAP.put(List.of("对接负责人", "负责人", "园区负责人", "跟进负责人"), Channel::setOwnerName);
        COLUMN_MAP.put(List.of("佣金比例", "佣金比例(%)", "佣金比例%", "佣金点数"), (c, v) -> c.setCommissionRate(parseRate(v)));
        COLUMN_MAP.put(List.of("合作协议", "是否签协议", "是否签合作协议", "协议"), (c, v) -> c.setAgreementSigned(parseYes(v) ? 1 : 0));
        COLUMN_MAP.put(List.of("累计推荐客户", "推荐客户数", "累计推荐"), (c, v) -> c.setReferralCount(parseInt(v)));
        COLUMN_MAP.put(List.of("累计成交客户", "成交客户数", "累计成交"), (c, v) -> c.setDealCount(parseInt(v)));
        COLUMN_MAP.put(List.of("状态", "合作状态"), (c, v) -> c.setStatus(v.contains("暂停") || v.contains("停") ? 0 : 1));
        COLUMN_MAP.put(List.of("备注", "说明"), Channel::setRemark);
    }

    private final ChannelMapper channelMapper;
    private final ChannelFollowService channelFollowService;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        List<List<String>> rows = readRows(file);

        int headerRow = findHeaderRow(rows);
        if (headerRow < 0) {
            throw new BizException("没找到表头行(应含「中介名称」或「公司名称」等名称列)");
        }
        Map<String, Integer> colIndex = new HashMap<>();
        List<String> header = rows.get(headerRow);
        for (int i = 0; i < header.size(); i++) {
            colIndex.putIfAbsent(normalize(header.get(i)), i);
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int r = headerRow + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Channel channel = new Channel();
            for (Map.Entry<List<String>, BiConsumer<Channel, String>> e : COLUMN_MAP.entrySet()) {
                String v = valueOf(row, colIndex, e.getKey());
                if (!v.isBlank()) {
                    e.getValue().accept(channel, v);
                }
            }
            // 名称为空视为空行,静默跳过
            if (channel.getName() == null || channel.getName().isBlank()) {
                continue;
            }
            if (exists(channel.getName(), channel.getPhone())) {
                skipped++;
                continue;
            }
            try {
                if (channel.getAgencyType() == null) channel.setAgencyType("中介公司");
                if (channel.getStatus() == null) channel.setStatus(1);
                channel.setAgencyNo(channelFollowService.nextAgencyNo());
                channelMapper.insert(channel);
                imported++;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                log.warn("[crm] 中介导入第 {} 行编号冲突", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + channel.getName() + "」编号冲突,请重新导入");
            } catch (Exception ex) {
                log.warn("[crm] 中介导入第 {} 行失败", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + channel.getName() + "」保存失败,请检查该行内容(如文字过长)");
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    // ==================== 各格式读成「行 × 列」 ====================

    private List<List<String>> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".tsv")) {
                return readText(bytes);
            }
            if (name.endsWith(".docx")) {
                return readDocx(bytes);
            }
            if (name.endsWith(".doc") || name.endsWith(".wps")) {
                throw new BizException("老版 Word(.doc)请另存为 .docx 或 Excel 后再导入");
            }
            // 其余(xlsx/xls/et 及未知扩展名)交给 POI 按文件内容识别
            return readWorkbook(bytes);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[crm] 中介导入解析失败 file={}", name, e);
            throw new BizException("文件解析失败,支持 Excel(.xlsx/.xls)、CSV/TXT、Word(.docx 表格)");
        }
    }

    private List<List<String>> readWorkbook(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes); Workbook wb = WorkbookFactory.create(in)) {
            // 优先取能找到表头的那张 sheet
            List<List<String>> first = null;
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                List<List<String>> rows = sheetRows(wb.getSheetAt(s));
                if (first == null && !rows.isEmpty()) first = rows;
                if (findHeaderRow(rows) >= 0) return rows;
            }
            return first == null ? List.of() : first;
        }
    }

    private List<List<String>> sheetRows(Sheet sheet) {
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

    private List<List<String>> readText(byte[] bytes) {
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

    private List<List<String>> readDocx(byte[] bytes) throws Exception {
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
                if (findHeaderRow(rows) >= 0) return rows;
            }
            if (first == null) {
                throw new BizException("Word 文档里没有表格,请把中介信息整理成表格后再导入");
            }
            return first;
        }
    }

    // ==================== 工具 ====================

    private static int findHeaderRow(List<List<String>> rows) {
        for (int r = 0; r < Math.min(rows.size(), HEADER_SCAN_ROWS); r++) {
            for (String cell : rows.get(r)) {
                if (NAME_HEADERS.contains(normalize(cell))) {
                    return r;
                }
            }
        }
        return -1;
    }

    private static String valueOf(List<String> row, Map<String, Integer> colIndex, List<String> aliases) {
        for (String alias : aliases) {
            Integer idx = colIndex.get(alias);
            if (idx != null && idx < row.size()) {
                String v = row.get(idx);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return "";
    }

    private boolean exists(String name, String phone) {
        return channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getName, name)
                .eq(phone != null && !phone.isBlank(), Channel::getPhone, phone)) > 0;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00a0*＊]", "").replace('（', '(').replace('）', ')');
    }

    /** 「A」「A级」「核心」都归到 A-核心合作 这类标准值 */
    static String normalizeGrade(String v) {
        String s = v.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("A") || s.contains("核心")) return "A-核心合作";
        if (s.startsWith("B") || s.contains("一般")) return "B-一般合作";
        if (s.startsWith("C") || s.contains("潜在")) return "C-潜在合作";
        return v.trim();
    }

    /** 「2%」「2」都按 2% 处理;不猜 0.02 这类小数(0.5% 的佣金也真实存在),原样入库 */
    static BigDecimal parseRate(String v) {
        try {
            return new BigDecimal(v.replace("%", "").replace("％", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean parseYes(String v) {
        String s = v.trim();
        return s.startsWith("是") || s.startsWith("已") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("yes") || s.equals("1");
    }

    static Integer parseInt(String v) {
        try {
            return new BigDecimal(v.trim()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
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
