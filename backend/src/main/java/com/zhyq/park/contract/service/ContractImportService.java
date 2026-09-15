package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 合同表格导入。按表头匹配字段，导入后均为草稿，需人工确认后再提交审批。 */
@Service
@RequiredArgsConstructor
public class ContractImportService {
    private final ContractMapper contractMapper;
    private final BizTenantMapper tenantMapper;
    private final ProjectMapper projectMapper;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请选择合同 Excel 或 CSV 文件");
        List<List<String>> rows = readRows(file);
        int headerRow = findHeader(rows);
        if (headerRow < 0) throw new BizException("没找到表头行，应包含合同编号、租客或合同起始日期");
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(headerRow);
        for (int i = 0; i < header.size(); i++) columns.putIfAbsent(norm(header.get(i)), i);
        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int r = headerRow + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(v -> v == null || v.isBlank())) continue;
            try {
                Contract c = new Contract();
                c.setCode(value(row, columns, "合同编号", "合同号", "编号"));
                c.setContractType(intValue(value(row, columns, "合同类型", "类型"), 1));
                c.setTenantRefId(resolveTenant(value(row, columns, "租客", "租户", "承租方", "租客名称")));
                c.setProjectId(resolveProject(value(row, columns, "园区", "项目", "园区名称")));
                c.setStartDate(dateValue(value(row, columns, "起始日期", "起租日", "开始日期")));
                c.setEndDate(dateValue(value(row, columns, "结束日期", "到期日", "结束日期")));
                c.setRentPrice(decimal(value(row, columns, "租赁单价", "租金单价", "单价")));
                c.setPropertyPrice(decimal(value(row, columns, "物业单价", "物业费单价")));
                c.setRentArea(decimal(value(row, columns, "租赁面积", "面积")));
                c.setDeposit(decimal(value(row, columns, "保证金", "押金")));
                c.setPayCycle(intValue(value(row, columns, "付款周期", "缴费周期"), 3));
                c.setFreeMonths(intValue(value(row, columns, "免租月数", "免租期"), 0));
                c.setRemark(value(row, columns, "备注", "说明"));
                c.setStatus(1);
                if (c.getCode() == null || c.getCode().isBlank()) throw new BizException("合同编号为空");
                if (contractMapper.selectCount(new LambdaQueryWrapper<Contract>().eq(Contract::getCode, c.getCode())) > 0) { skipped++; continue; }
                contractMapper.insert(c); imported++;
            } catch (Exception e) {
                errors.add("第 " + (r + 1) + " 行: " + (e instanceof BizException ? e.getMessage() : "字段格式不正确"));
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    private Long resolveTenant(String name) {
        if (name == null || name.isBlank()) throw new BizException("租客不能为空");
        BizTenant t = tenantMapper.selectOne(new LambdaQueryWrapper<BizTenant>().eq(BizTenant::getName, name));
        if (t == null) throw new BizException("找不到租客「" + name + "」");
        return t.getId();
    }
    private Long resolveProject(String name) {
        if (name == null || name.isBlank()) throw new BizException("园区不能为空");
        Project p = projectMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getName, name));
        if (p == null) throw new BizException("找不到园区「" + name + "」");
        return p.getId();
    }
    private static String value(List<String> row, Map<String,Integer> cols, String... names) {
        for (String n : names) { Integer i = cols.get(norm(n)); if (i != null && i < row.size()) return Optional.ofNullable(row.get(i)).orElse("").trim(); }
        return "";
    }
    private static String norm(String s) { return s == null ? "" : s.replaceAll("[\\s()（）]", "").toLowerCase(Locale.ROOT); }
    private static int findHeader(List<List<String>> rows) { for (int i=0;i<Math.min(10, rows.size());i++) { String s=String.join("", rows.get(i)); if (s.contains("合同编号") || s.contains("租客") || s.contains("起租日")) return i; } return -1; }
    private static int intValue(String s, int d) { if (s == null || s.isBlank()) return d; return (int) Double.parseDouble(s.replace(",", "")); }
    private static java.math.BigDecimal decimal(String s) { return s == null || s.isBlank() ? null : new java.math.BigDecimal(s.replace(",", "")); }
    private static LocalDate dateValue(String s) { if (s == null || s.isBlank()) return null; for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("yyyy年M月d日"))) try { return LocalDate.parse(s.replace(".", "/"), f); } catch (Exception ignored) {} throw new BizException("日期格式不正确: " + s); }
    private List<List<String>> readRows(MultipartFile file) {
        try {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            if (name.endsWith(".csv") || name.endsWith(".txt")) return Arrays.stream(new String(file.getBytes(), StandardCharsets.UTF_8).split("\\R", -1)).map(line -> Arrays.asList(line.split(",", -1))).toList();
            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
                List<List<String>> out = new ArrayList<>();
                Sheet s = wb.getSheetAt(0); DataFormatter fmt = new DataFormatter();
                for (Row row : s) { List<String> cells = new ArrayList<>(); for (int c=0;c<row.getLastCellNum();c++) cells.add(fmt.formatCellValue(row.getCell(c))); out.add(cells); }
                return out;
            }
        } catch (Exception e) { throw new BizException("文件解析失败，仅支持 .xlsx、.xls、.csv"); }
    }
}
