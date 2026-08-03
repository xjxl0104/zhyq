package com.zhyq.park.vending.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.service.SpreadsheetCellReader;
import com.zhyq.park.vending.model.VendingImportData;
import com.zhyq.park.vending.model.VendingImportType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class VendingImportParser {
    private static final Set<String> MACHINE_STATUSES = Set.of("在线", "离线", "故障", "停用");
    private static final Set<String> SALE_STATUSES = Set.of("已支付", "已完成", "已退款", "部分退款", "已取消");
    private static final Set<String> FAULT_STATUSES = Set.of("待处理", "处理中", "已恢复", "已关闭");
    private static final Set<String> SETTLEMENT_STATUSES = Set.of("待对账", "已对账", "已结算", "异常");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"));

    private final SpreadsheetCellReader cellReader = new SpreadsheetCellReader();

    public VendingImportData parse(VendingImportType type, byte[] bytes) {
        if (type == null || bytes == null || bytes.length == 0) {
            throw new BizException("导入类型和文件不能为空");
        }
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet(type.sheetName());
            if (sheet == null) {
                throw new BizException("缺少工作表‘" + type.sheetName() + "’");
            }
            validateHeaders(type, sheet.getRow(1));
            List<VendingImportData.RowData> rows = new ArrayList<>();
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (blank(row, type.headers().size())) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                Map<String, String> formulas = new LinkedHashMap<>();
                for (int column = 0; column < type.headers().size(); column++) {
                    var value = cellReader.read(row == null ? null : row.getCell(column));
                    String header = type.headers().get(column);
                    values.put(header, normalize(value.displayValue()));
                    if (value.formula() != null) {
                        formulas.put(header, value.formula());
                    }
                }
                List<String> errors = validate(type, values);
                rows.add(new VendingImportData.RowData(
                        rowIndex + 1, Map.copyOf(values), Map.copyOf(formulas), List.copyOf(errors)));
            }
            return new VendingImportData(type, type.headers(), List.copyOf(rows));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("无法读取售货机导入文件: " + e.getMessage());
        }
    }

    private void validateHeaders(VendingImportType type, Row row) {
        if (row == null) {
            throw new BizException("第2行必须是标准表头");
        }
        for (int i = 0; i < type.headers().size(); i++) {
            String actual = normalize(cellReader.read(row.getCell(i)).displayValue());
            String expected = type.headers().get(i);
            if (!expected.equals(actual)) {
                throw new BizException("第" + (i + 1) + "列表头应为‘" + expected + "’，实际为‘" + actual + "’");
            }
        }
        for (int i = type.headers().size(); i < Math.max(row.getLastCellNum(), 0); i++) {
            if (!normalize(cellReader.read(row.getCell(i)).displayValue()).isEmpty()) {
                throw new BizException("标准模板不允许增加表头列");
            }
        }
    }

    private List<String> validate(VendingImportType type, Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        switch (type) {
            case MACHINE -> validateMachine(values, errors);
            case SALE -> validateSale(values, errors);
            case RESTOCK -> validateRestock(values, errors);
            case FAULT -> validateFault(values, errors);
            case RECONCILIATION -> validateReconciliation(values, errors);
        }
        return errors;
    }

    private void validateMachine(Map<String, String> row, List<String> errors) {
        required(row, errors, "厂商机器编号");
        required(row, errors, "机器名称");
        allowed(row, errors, "运行状态", MACHINE_STATUSES);
        optionalDateTime(row, errors, "最后在线时间");
    }

    private void validateSale(Map<String, String> row, List<String> errors) {
        required(row, errors, "厂商订单号", "机器编号", "商品名称", "支付时间");
        positiveInteger(row, errors, "行号");
        positiveInteger(row, errors, "数量");
        BigDecimal original = nonNegative(row, errors, "原价金额");
        nonNegative(row, errors, "优惠金额");
        BigDecimal paid = nonNegative(row, errors, "实付金额");
        if (original != null && paid != null && paid.compareTo(original) > 0) {
            errors.add("实付金额不能大于原价金额");
        }
        dateTime(row, errors, "支付时间");
        allowed(row, errors, "订单状态", SALE_STATUSES);
    }

    private void validateRestock(Map<String, String> row, List<String> errors) {
        required(row, errors, "厂商补货单号", "机器编号", "商品名称", "补货时间");
        positiveInteger(row, errors, "补货数量");
        dateTime(row, errors, "补货时间");
    }

    private void validateFault(Map<String, String> row, List<String> errors) {
        required(row, errors, "厂商故障编号", "机器编号", "故障类型", "发生时间");
        LocalDateTime occurred = dateTime(row, errors, "发生时间");
        LocalDateTime recovered = optionalDateTime(row, errors, "恢复时间");
        if (occurred != null && recovered != null && recovered.isBefore(occurred)) {
            errors.add("恢复时间不能早于发生时间");
        }
        allowed(row, errors, "状态", FAULT_STATUSES);
    }

    private void validateReconciliation(Map<String, String> row, List<String> errors) {
        required(row, errors, "厂商结算单号", "结算周期开始", "结算周期结束");
        LocalDate start = date(row, errors, "结算周期开始");
        LocalDate end = date(row, errors, "结算周期结束");
        if (start != null && end != null && end.isBefore(start)) {
            errors.add("结算周期结束不能早于结算周期开始");
        }
        nonNegative(row, errors, "销售总额");
        nonNegative(row, errors, "退款");
        nonNegative(row, errors, "平台费用");
        nonNegative(row, errors, "结算净额");
        allowed(row, errors, "状态", SETTLEMENT_STATUSES);
    }

    private void required(Map<String, String> row, List<String> errors, String... headers) {
        for (String header : headers) {
            if (row.getOrDefault(header, "").isBlank()) {
                errors.add(header + "不能为空");
            }
        }
    }

    private void allowed(Map<String, String> row, List<String> errors, String header, Set<String> allowed) {
        String value = row.getOrDefault(header, "");
        if (value.isBlank()) {
            errors.add(header + "不能为空");
        } else if (!allowed.contains(value)) {
            errors.add(header + "不在允许值中: " + value);
        }
    }

    private Integer positiveInteger(Map<String, String> row, List<String> errors, String header) {
        String value = row.getOrDefault(header, "");
        try {
            int parsed = new BigDecimal(cleanNumber(value)).intValueExact();
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (Exception e) {
            errors.add(header + "必须是正整数");
            return null;
        }
    }

    private BigDecimal nonNegative(Map<String, String> row, List<String> errors, String header) {
        String value = row.getOrDefault(header, "");
        try {
            BigDecimal parsed = new BigDecimal(cleanNumber(value));
            if (parsed.signum() < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (Exception e) {
            errors.add(header + "必须是非负数字");
            return null;
        }
    }

    private LocalDateTime optionalDateTime(Map<String, String> row, List<String> errors, String header) {
        if (row.getOrDefault(header, "").isBlank()) {
            return null;
        }
        return dateTime(row, errors, header);
    }

    private LocalDateTime dateTime(Map<String, String> row, List<String> errors, String header) {
        String value = row.getOrDefault(header, "");
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported standard representation.
            }
        }
        errors.add(header + "格式应为 yyyy-MM-dd HH:mm:ss");
        return null;
    }

    private LocalDate date(Map<String, String> row, List<String> errors, String header) {
        String value = row.getOrDefault(header, "");
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported standard representation.
            }
        }
        errors.add(header + "格式应为 yyyy-MM-dd");
        return null;
    }

    private boolean blank(Row row, int columns) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < columns; i++) {
            if (!normalize(cellReader.read(row.getCell(i)).displayValue()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cleanNumber(String value) {
        return normalize(value).replace(",", "").replace("，", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace('\u00a0', ' ');
    }
}
