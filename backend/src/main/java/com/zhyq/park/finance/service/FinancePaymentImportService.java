package com.zhyq.park.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.ContractRef;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.ContractRefMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 历史收款导入：把过往已经收到的钱按「合同编号 + 费用类型 + 到账日 + 金额」登记为正式收款。
 *
 * <p>流程分两步：预览（只算不写）→ 确认（整份文件零错误才写入，单个事务）。
 * 每笔都经 {@link PaymentService#历史收款} 落账，因此幂等、超收、并发扣减、流水、收据与手工收款完全一致，
 * 登记后同样可以红冲。同一文件重复上传不会重复入账（行级幂等键）。</p>
 *
 * <p>分摊规则：同一合同同一费用类型下，按账期从早到晚依次冲抵未收账单；
 * 填了「账期」列则只冲抵该账期。金额超过可冲抵合计时该行报错，不做任何写入。</p>
 */
@Service
@RequiredArgsConstructor
public class FinancePaymentImportService {

    static final String PAY_NO_PREFIX = "HIS-";
    private static final int MAX_ROWS = 2000;
    private static final String DEFAULT_PAY_METHOD = "转账";
    private static final List<Integer> COLLECTIBLE_STATUSES = List.of(3, 4, 6);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d"), DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"), DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private final BillMapper billMapper;
    private final ContractRefMapper contractRefMapper;
    private final ReceivableRegisterMapper registerMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public ImportReport preview(MultipartFile file) {
        return analyze(file).report();
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportReport confirm(MultipartFile file, String operator) {
        Analysis analysis = analyze(file);
        ImportReport report = analysis.report();
        if (report.errorCount() > 0) {
            throw new BizException("文件中有 " + report.errorCount() + " 行无法登记，请先按预览提示修正后重新上传");
        }
        for (RowResult row : analysis.rows()) {
            if (!STATUS_OK.equals(row.status())) continue;
            for (int i = 0; i < row.allocations().size(); i++) {
                Allocation allocation = row.allocations().get(i);
                paymentService.历史收款(allocation.billId(), allocation.amount(), row.payMethod(),
                        PAY_NO_PREFIX + row.rowKey() + "-" + (i + 1), row.payDate().atTime(LocalTime.NOON),
                        operator, row.remark());
            }
        }
        return report;
    }

    private Analysis analyze(MultipartFile file) {
        List<ImportLine> lines = parse(file);
        Set<String> codes = lines.stream().map(ImportLine::contractCode).filter(StringUtils::hasText)
                .map(String::trim).collect(Collectors.toSet());
        Map<String, ContractRef> contracts = new HashMap<>();
        if (!codes.isEmpty()) {
            for (ContractRef contract : contractRefMapper.selectList(
                    new LambdaQueryWrapper<ContractRef>().in(ContractRef::getCode, codes))) {
                contracts.put(contract.getCode(), contract);
            }
        }
        Map<Long, List<Bill>> billsByContract = loadCollectibleBills(contracts.values().stream()
                .map(ContractRef::getId).toList());
        Set<String> imported = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                        .likeRight(Payment::getPayNo, PAY_NO_PREFIX)).stream()
                .map(payment -> payment.getPayNo().substring(PAY_NO_PREFIX.length()))
                .filter(no -> no.length() >= ROW_KEY_LENGTH).map(no -> no.substring(0, ROW_KEY_LENGTH))
                .collect(Collectors.toSet());
        List<RowResult> rows = plan(lines, contracts, billsByContract, imported, LocalDate.now(BUSINESS_ZONE));
        return new Analysis(rows, summarize(rows));
    }

    private Map<Long, List<Bill>> loadCollectibleBills(List<Long> contractIds) {
        Map<Long, List<Bill>> result = new HashMap<>();
        if (contractIds.isEmpty()) return result;
        Map<Long, Long> registerToContract = new HashMap<>();
        for (ReceivableRegister register : registerMapper.selectList(new LambdaQueryWrapper<ReceivableRegister>()
                .in(ReceivableRegister::getContractId, contractIds))) {
            registerToContract.put(register.getId(), register.getContractId());
        }
        LambdaQueryWrapper<Bill> query = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getDirection, BillMetrics.DIRECTION_RECEIVE)
                .in(Bill::getStatus, COLLECTIBLE_STATUSES)
                .and(wrapper -> {
                    wrapper.in(Bill::getContractId, contractIds);
                    if (!registerToContract.isEmpty()) wrapper.or().in(Bill::getReceivableRegisterId, registerToContract.keySet());
                });
        for (Bill bill : billMapper.selectList(query)) {
            Long contractId = bill.getContractId() != null ? bill.getContractId()
                    : registerToContract.get(bill.getReceivableRegisterId());
            if (contractId != null) result.computeIfAbsent(contractId, ignored -> new ArrayList<>()).add(bill);
        }
        return result;
    }

    // ───────────────────────── 纯计算（不读库、不写库，便于单测） ─────────────────────────

    static final String STATUS_OK = "ok";
    static final String STATUS_SKIPPED = "skipped";
    static final String STATUS_ERROR = "error";
    static final int ROW_KEY_LENGTH = 24;

    static List<RowResult> plan(List<ImportLine> lines, Map<String, ContractRef> contracts,
                                Map<Long, List<Bill>> billsByContract, Set<String> importedRowKeys, LocalDate today) {
        Map<Long, Map<Long, BigDecimal>> remainingByContract = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        List<RowResult> results = new ArrayList<>();
        for (ImportLine line : lines) {
            results.add(planLine(line, contracts, billsByContract, importedRowKeys, today, remainingByContract, occurrences));
        }
        return results;
    }

    private static RowResult planLine(ImportLine line, Map<String, ContractRef> contracts,
                                      Map<Long, List<Bill>> billsByContract, Set<String> importedRowKeys, LocalDate today,
                                      Map<Long, Map<Long, BigDecimal>> remainingByContract, Map<String, Integer> occurrences) {
        String code = line.contractCode() == null ? "" : line.contractCode().trim();
        String feeType = line.feeType() == null ? "" : line.feeType().trim();
        LocalDate payDate = parseDate(line.payDate());
        BigDecimal amount = parseAmount(line.amount());
        String method = StringUtils.hasText(line.payMethod()) ? line.payMethod().trim() : DEFAULT_PAY_METHOD;
        String remark = StringUtils.hasText(line.remark()) ? "历史收款导入；" + line.remark().trim() : "历史收款导入";
        java.util.function.Function<String, RowResult> error = message -> new RowResult(line.rowNo(), code, feeType,
                payDate, amount, STATUS_ERROR, message, List.of(), "", method, remark);
        if (code.isEmpty()) return error.apply("合同编号为空");
        if (feeType.isEmpty()) return error.apply("费用类型为空");
        if (payDate == null) return error.apply("收款日期无法识别，请用 2026-03-15 这样的格式");
        if (payDate.isAfter(today)) return error.apply("收款日期晚于今天，历史收款只能登记已经到账的款项");
        if (amount == null) return error.apply("金额无法识别");
        if (amount.signum() <= 0) return error.apply("金额必须大于 0");
        if (amount.stripTrailingZeros().scale() > 2) return error.apply("金额最多两位小数");
        ContractRef contract = contracts.get(code);
        if (contract == null) return error.apply("系统里没有合同「" + code + "」，请先在合同列表导入或新增");
        YearMonthKey period = StringUtils.hasText(line.period()) ? YearMonthKey.parse(line.period()) : null;
        if (StringUtils.hasText(line.period()) && period == null) return error.apply("账期无法识别，请用 2026-03 这样的格式");

        String rowKey = rowKey(code, feeType, payDate, amount, occurrences);
        if (importedRowKeys.contains(rowKey)) {
            return new RowResult(line.rowNo(), code, feeType, payDate, amount, STATUS_SKIPPED,
                    "这一笔此前已经导入过，已自动跳过", List.of(), rowKey, method, remark);
        }
        List<Bill> sameFee = billsByContract.getOrDefault(contract.getId(), List.of()).stream()
                .filter(bill -> feeType.equals(bill.getFeeType() == null ? "" : bill.getFeeType().trim()))
                .sorted(Comparator.comparing(Bill::getPeriodStart, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Bill::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Bill::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        if (sameFee.isEmpty()) {
            String available = billsByContract.getOrDefault(contract.getId(), List.of()).stream()
                    .map(bill -> bill.getFeeType() == null ? "其他" : bill.getFeeType().trim()).distinct().sorted()
                    .collect(Collectors.joining("、"));
            return error.apply(available.isEmpty() ? "合同「" + code + "」下没有可登记收款的账单（可能尚未生成账单或已全部结清）"
                    : "合同「" + code + "」下没有「" + feeType + "」类待收账单，现有待收类型：" + available);
        }
        Map<Long, BigDecimal> remaining = remainingByContract.computeIfAbsent(contract.getId(), ignored -> new HashMap<>());
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal left = amount;
        BigDecimal collectible = BigDecimal.ZERO;
        for (Bill bill : sameFee) {
            if (period != null && !period.matches(bill.getPeriodStart())) continue;
            BigDecimal room = remaining.computeIfAbsent(bill.getId(), ignored -> remainingOf(bill));
            if (room.signum() <= 0) continue;
            collectible = collectible.add(room);
            if (left.signum() <= 0) continue;
            BigDecimal part = left.min(room);
            allocations.add(new Allocation(bill.getId(), bill.getCode(), bill.getPeriodStart(), part));
            left = left.subtract(part);
        }
        if (left.signum() > 0) {
            return error.apply("金额 " + plain(amount) + " 超过该合同「" + feeType + "」"
                    + (period == null ? "" : period.label() + "账期") + "待收合计 " + plain(collectible) + " 元");
        }
        for (Allocation allocation : allocations) remaining.merge(allocation.billId(), allocation.amount().negate(), BigDecimal::add);
        return new RowResult(line.rowNo(), code, feeType, payDate, amount, STATUS_OK,
                "将冲抵 " + allocations.size() + " 张账单", List.copyOf(allocations), rowKey, method, remark);
    }

    private static BigDecimal remainingOf(Bill bill) {
        return BillMetrics.nz(bill.getAmount()).add(BillMetrics.nz(bill.getLateFee())).subtract(BillMetrics.nz(bill.getPaidAmount()));
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static String rowKey(String code, String feeType, LocalDate payDate, BigDecimal amount, Map<String, Integer> occurrences) {
        String base = code + "|" + feeType + "|" + payDate + "|" + amount.stripTrailingZeros().toPlainString();
        int index = occurrences.merge(base, 1, Integer::sum);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((base + "|" + index).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.substring(0, ROW_KEY_LENGTH);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String text = raw.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try { return LocalDate.parse(text, format); } catch (DateTimeParseException ignored) { /* 试下一种格式 */ }
        }
        return null;
    }

    static BigDecimal parseAmount(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return new BigDecimal(raw.trim().replace(",", "").replace("，", "").replace("元", "").replace("¥", "").replace("￥", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ImportReport summarize(List<RowResult> rows) {
        int ok = 0, skipped = 0, errors = 0, bills = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (RowResult row : rows) {
            switch (row.status()) {
                case STATUS_OK -> { ok++; bills += row.allocations().size(); total = total.add(row.amount()); }
                case STATUS_SKIPPED -> skipped++;
                default -> errors++;
            }
        }
        return new ImportReport(rows.size(), ok, skipped, errors, bills, total, rows);
    }

    // ───────────────────────── 文件解析 ─────────────────────────

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("合同编号", "contractCode"), Map.entry("合同号", "contractCode"), Map.entry("合同编码", "contractCode"),
            Map.entry("费用类型", "feeType"), Map.entry("款项", "feeType"), Map.entry("费用", "feeType"),
            Map.entry("收款日期", "payDate"), Map.entry("到账日期", "payDate"), Map.entry("收款时间", "payDate"),
            Map.entry("金额", "amount"), Map.entry("收款金额", "amount"), Map.entry("实收金额", "amount"),
            Map.entry("支付方式", "payMethod"), Map.entry("付款方式", "payMethod"),
            Map.entry("账期", "period"), Map.entry("对应账期", "period"),
            Map.entry("备注", "remark"), Map.entry("说明", "remark"));

    static List<ImportLine> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请选择历史收款 Excel 或 CSV 文件");
        List<List<String>> rows = readRows(file);
        int headerRow = -1;
        Map<String, Integer> columns = new HashMap<>();
        for (int r = 0; r < Math.min(rows.size(), 20) && headerRow < 0; r++) {
            Map<String, Integer> found = new HashMap<>();
            List<String> cells = rows.get(r);
            for (int c = 0; c < cells.size(); c++) {
                String key = HEADER_ALIASES.get(cells.get(c) == null ? "" : cells.get(c).replaceAll("\\s+", ""));
                if (key != null) found.putIfAbsent(key, c);
            }
            if (found.containsKey("contractCode") && found.containsKey("amount")) { headerRow = r; columns = found; }
        }
        if (headerRow < 0) throw new BizException("没找到表头行，表头需包含：合同编号、费用类型、收款日期、金额");
        for (String required : List.of("feeType", "payDate")) {
            if (!columns.containsKey(required)) throw new BizException("表头缺少「" + (required.equals("feeType") ? "费用类型" : "收款日期") + "」列");
        }
        List<ImportLine> lines = new ArrayList<>();
        for (int r = headerRow + 1; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            if (cells.stream().allMatch(value -> value == null || value.isBlank())) continue;
            Map<String, Integer> cols = columns;
            java.util.function.Function<String, String> cell = key -> {
                Integer index = cols.get(key);
                return index == null || index >= cells.size() ? null : cells.get(index);
            };
            lines.add(new ImportLine(r + 1, cell.apply("contractCode"), cell.apply("feeType"), cell.apply("payDate"),
                    cell.apply("amount"), cell.apply("payMethod"), cell.apply("period"), cell.apply("remark")));
            if (lines.size() > MAX_ROWS) throw new BizException("一次最多导入 " + MAX_ROWS + " 行，请拆分文件");
        }
        if (lines.isEmpty()) throw new BizException("表头下面没有数据行");
        return lines;
    }

    private static List<List<String>> readRows(MultipartFile file) {
        try {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                String text = new String(file.getBytes(), StandardCharsets.UTF_8);
                if (text.startsWith("﻿")) text = text.substring(1);
                return Arrays.stream(text.split("\\R", -1)).map(FinancePaymentImportService::splitCsvLine).toList();
            }
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
                List<List<String>> out = new ArrayList<>();
                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) cells.add(cellText(row.getCell(c), formatter));
                    out.add(cells);
                }
                return out;
            }
        } catch (Exception exception) {
            throw new BizException("文件解析失败，仅支持 .xlsx、.xls、.csv");
        }
    }

    /** 支持双引号包裹的字段（如 "1,000.50"）与 "" 转义的引号。 */
    static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else current.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { cells.add(current.toString()); current.setLength(0); }
            else current.append(ch);
        }
        cells.add(current.toString());
        return cells;
    }

    /** 日期单元格统一转 yyyy-MM-dd，避免被本地显示格式（如 3/15/26）带偏；数字单元格取原值，不带千分位。 */
    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            return new BigDecimal(String.valueOf(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell);
    }

    private record YearMonthKey(int year, int month) {
        static YearMonthKey parse(String raw) {
            String text = raw.trim().replace('/', '-').replace('.', '-');
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{4})-?(\\d{1,2})(?:-\\d{1,2})?$").matcher(text);
            if (!matcher.matches()) return null;
            int month = Integer.parseInt(matcher.group(2));
            return month >= 1 && month <= 12 ? new YearMonthKey(Integer.parseInt(matcher.group(1)), month) : null;
        }
        boolean matches(LocalDate date) { return date != null && date.getYear() == year && date.getMonthValue() == month; }
        String label() { return year + "-" + String.format("%02d", month); }
    }

    private record Analysis(List<RowResult> rows, ImportReport report) {}

    public record ImportLine(int rowNo, String contractCode, String feeType, String payDate, String amount,
                             String payMethod, String period, String remark) {}

    public record Allocation(Long billId, String billCode, LocalDate periodStart, BigDecimal amount) {}

    public record RowResult(int rowNo, String contractCode, String feeType, LocalDate payDate, BigDecimal amount,
                            String status, String message, List<Allocation> allocations, String rowKey,
                            String payMethod, String remark) {}

    public record ImportReport(int totalRows, int okCount, int skippedCount, int errorCount, int billCount,
                               BigDecimal totalAmount, List<RowResult> rows) {}
}
