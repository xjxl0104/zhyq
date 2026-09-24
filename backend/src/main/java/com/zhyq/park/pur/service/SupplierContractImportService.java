package com.zhyq.park.pur.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.entity.SupplierContract;
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 供应商合同台账导入。按供应商名称关联现有供应商，导入记录均先保存为草稿，
 * 原始合同附件可在列表中通过「编辑」继续上传。
 */
@Service
@RequiredArgsConstructor
public class SupplierContractImportService {

    private static final int ST_DRAFT = 1;

    private final SupplierContractMapper contractMapper;
    private final SupplierMapper supplierMapper;

    public record ImportResult(int imported, int skipped, List<String> errors) { }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择供应商合同 Excel 或 CSV 文件");
        }
        List<List<String>> rows = readRows(file);
        int headerRow = findHeader(rows);
        if (headerRow < 0) {
            throw new BizException("未找到表头行，应包含供应商、合同名称或合同金额");
        }

        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(headerRow);
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(norm(header.get(i)), i);
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        String prefix = "GYSHT-" + LocalDate.now().getYear() + "-";
        int sequence = nextSequence(prefix);

        for (int r = headerRow + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            try {
                SupplierContract contract = new SupplierContract();
                contract.setSupplierId(resolveSupplier(value(row, columns,
                        "供应商", "供应商名称", "合作方")));
                contract.setName(required(value(row, columns,
                        "合同名称", "名称", "合同名"), "合同名称不能为空"));
                contract.setContractType(value(row, columns, "合同类型", "类型"));
                contract.setAmount(decimal(value(row, columns, "合同金额", "金额", "含税金额")));
                contract.setSignDate(dateValue(value(row, columns, "签订日期", "签约日期")));
                contract.setStartDate(dateValue(value(row, columns, "生效日期", "开始日期", "起始日期")));
                contract.setEndDate(dateValue(value(row, columns, "到期日期", "结束日期", "终止日期")));
                if (contract.getStartDate() != null && contract.getEndDate() != null
                        && contract.getEndDate().isBefore(contract.getStartDate())) {
                    throw new BizException("到期日期不能早于生效日期");
                }
                contract.setPayCycle(value(row, columns, "结算周期", "付款周期"));
                contract.setPayTerms(value(row, columns, "付款方式", "付款方式账期", "账期"));
                contract.setRemark(value(row, columns, "备注", "说明"));
                contract.setStatus(ST_DRAFT);
                contract.setCode(prefix + String.format("%04d", sequence++));
                contractMapper.insert(contract);
                imported++;
            } catch (Exception e) {
                if (e instanceof BizException && e.getMessage() != null
                        && e.getMessage().contains("供应商不存在")) {
                    skipped++;
                }
                errors.add("第 " + (r + 1) + " 行: "
                        + (e instanceof BizException ? e.getMessage() : "字段格式不正确"));
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    private int nextSequence(String prefix) {
        String max = contractMapper.selectMaxCodeIncludingDeleted(prefix);
        if (!org.springframework.util.StringUtils.hasText(max)) {
            return 1;
        }
        try {
            return Integer.parseInt(max.substring(prefix.length())) + 1;
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return 1;
        }
    }

    private Long resolveSupplier(String name) {
        if (!org.springframework.util.StringUtils.hasText(name)) {
            throw new BizException("供应商不能为空");
        }
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, name));
        if (supplier == null) {
            throw new BizException("供应商不存在: " + name);
        }
        return supplier.getId();
    }

    private static String required(String value, String message) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value;
    }

    private static String value(List<String> row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(norm(name));
            if (index != null && index < row.size()) {
                return Optional.ofNullable(row.get(index)).orElse("").trim();
            }
        }
        return "";
    }

    private static String norm(String value) {
        return value == null ? "" : value.replaceAll("[\\s()（）]", "").toLowerCase(Locale.ROOT);
    }

    private static int findHeader(List<List<String>> rows) {
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String row = String.join("", rows.get(i));
            if (row.contains("供应商") || row.contains("合同名称") || row.contains("合同金额")) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal decimal(String value) {
        return !org.springframework.util.StringUtils.hasText(value)
                ? null : new BigDecimal(value.replace(",", "").replace("¥", "").replace("元", ""));
    }

    private static LocalDate dateValue(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy年M月d日"))) {
            try {
                return LocalDate.parse(value.replace(".", "/"), formatter);
            } catch (Exception ignored) {
                // 尝试下一个常用日期格式。
            }
        }
        throw new BizException("日期格式不正确: " + value);
    }

    private static List<List<String>> readRows(MultipartFile file) {
        try {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return Arrays.stream(new String(file.getBytes(), StandardCharsets.UTF_8)
                                .split("\\R", -1))
                        .map(line -> Arrays.asList(line.split(",", -1)))
                        .toList();
            }
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();
                List<List<String>> result = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        cells.add(formatter.formatCellValue(row.getCell(column)));
                    }
                    result.add(cells);
                }
                return result;
            }
        } catch (Exception e) {
            throw new BizException("文件解析失败，仅支持 .xlsx、.xls、.csv");
        }
    }
}
