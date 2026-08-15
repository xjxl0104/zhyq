package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.service.SpreadsheetCellReader;
import com.zhyq.park.property.entity.ResponsibleUnit;
import com.zhyq.park.property.mapper.ResponsibleUnitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任单位 Excel 导入。
 *
 * <p>表头严格校验 + 模板下载的做法沿用 {@code VendingImportParser};
 * 解析用 POI 并只读公式缓存值(见 {@link SpreadsheetCellReader}),不在服务端求值。</p>
 *
 * <p>与 Vending 的差别:责任单位是主数据、量小且无下游依赖,
 * 故不引入 sys_import_batch 两阶段预览,直接"解析→校验→按名称 upsert",
 * 校验不通过的行整体拒绝,不做部分提交,避免主数据出现半成品。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsibleUnitImportService {

    private final ResponsibleUnitMapper unitMapper;

    /** 模板表头,导入时逐列严格比对 */
    public static final List<String> HEADERS = List.of(
            "单位名称", "单位类型", "联系人", "联系电话", "服务范围", "备注");

    private static final String SHEET_NAME = "责任单位";
    private static final List<String> UNIT_TYPES = List.of("内部部门", "外部供应商", "物业", "施工方");

    /** 解析结果:成功的行 + 逐行错误 */
    public record ParseResult(List<ResponsibleUnit> rows, List<String> errors) {}

    public ParseResult parse(MultipartFile file, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        List<ResponsibleUnit> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SpreadsheetCellReader reader = new SpreadsheetCellReader();

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }
            if (sheet == null) {
                throw new BizException("文件中没有可读的工作表");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BizException("第 1 行必须是表头");
            }
            for (int c = 0; c < HEADERS.size(); c++) {
                String actual = cell(reader, headerRow, c);
                if (!HEADERS.get(c).equals(actual)) {
                    throw new BizException(String.format(
                            "表头第 %d 列应为「%s」,实际为「%s」。请下载模板后填写",
                            c + 1, HEADERS.get(c), actual == null ? "空" : actual));
                }
            }

            // 同一文件内按名称去重,避免自身重复行互相覆盖
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = cell(reader, row, 0);
                String unitType = cell(reader, row, 1);
                String contact = cell(reader, row, 2);
                String phone = cell(reader, row, 3);
                String scope = cell(reader, row, 4);
                String remark = cell(reader, row, 5);

                // 整行为空视为文件末尾的空白行,跳过
                if (isAllBlank(name, unitType, contact, phone, scope, remark)) continue;

                int lineNo = r + 1;
                if (name == null || name.isBlank()) {
                    errors.add("第 " + lineNo + " 行:单位名称不能为空");
                    continue;
                }
                if (name.length() > 128) {
                    errors.add("第 " + lineNo + " 行:单位名称超过 128 字");
                    continue;
                }
                Integer dup = seen.put(name, lineNo);
                if (dup != null) {
                    errors.add("第 " + lineNo + " 行:单位名称「" + name + "」与第 " + dup + " 行重复");
                    continue;
                }
                if (unitType != null && !unitType.isBlank() && !UNIT_TYPES.contains(unitType)) {
                    errors.add("第 " + lineNo + " 行:单位类型「" + unitType + "」无效,应为 "
                            + String.join("/", UNIT_TYPES));
                    continue;
                }
                if (phone != null && phone.length() > 20) {
                    errors.add("第 " + lineNo + " 行:联系电话超过 20 字");
                    continue;
                }

                ResponsibleUnit u = new ResponsibleUnit();
                u.setName(name.trim());
                u.setUnitType(blankToNull(unitType));
                u.setContact(blankToNull(contact));
                u.setContactPhone(blankToNull(phone));
                u.setServiceScope(blankToNull(scope));
                u.setRemark(blankToNull(remark));
                u.setProjectId(projectId);
                u.setEnabled(1);
                rows.add(u);
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("文件读取失败:" + e.getMessage());
        } catch (Exception e) {
            log.warn("责任单位导入解析失败", e);
            throw new BizException("文件解析失败,请确认是 xls/xlsx 格式");
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BizException("文件中没有数据行");
        }
        return new ParseResult(rows, errors);
    }

    /**
     * 导入落库:存在同名则更新,否则新增。
     * 有任何一行校验失败即整体拒绝,主数据不做部分提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importUnits(MultipartFile file, Long projectId) {
        ParseResult parsed = parse(file, projectId);
        if (!parsed.errors().isEmpty()) {
            throw new BizException("导入未执行,存在 " + parsed.errors().size()
                    + " 处问题:\n" + String.join("\n", limit(parsed.errors(), 10)));
        }
        int created = 0;
        int updated = 0;
        for (ResponsibleUnit u : parsed.rows()) {
            LambdaQueryWrapper<ResponsibleUnit> q = new LambdaQueryWrapper<ResponsibleUnit>()
                    .eq(ResponsibleUnit::getName, u.getName());
            if (projectId == null) {
                q.isNull(ResponsibleUnit::getProjectId);
            } else {
                q.eq(ResponsibleUnit::getProjectId, projectId);
            }
            ResponsibleUnit exist = unitMapper.selectOne(q);
            if (exist == null) {
                unitMapper.insert(u);
                created++;
            } else {
                u.setId(exist.getId());
                u.setVersion(exist.getVersion());
                unitMapper.updateById(u);
                updated++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", parsed.rows().size());
        m.put("created", created);
        m.put("updated", updated);
        return m;
    }

    /** 生成导入模板,含表头、示例行与类型说明 */
    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, 18 * 256);
            }
            Row sample = sheet.createRow(1);
            String[] demo = {"华强电梯维保", "外部供应商", "张工", "13800000000", "电梯", "季度保养"};
            for (int i = 0; i < demo.length; i++) {
                sample.createCell(i).setCellValue(demo[i]);
            }
            Row tip = sheet.createRow(3);
            tip.createCell(0).setCellValue("填写说明:单位名称必填且不可重复;单位类型可选 "
                    + String.join("/", UNIT_TYPES) + ",留空表示未分类;示例行请删除后再导入");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException("模板生成失败:" + e.getMessage());
        }
    }

    private static String cell(SpreadsheetCellReader reader, Row row, int idx) {
        if (row == null) return null;
        SpreadsheetCellReader.CellValue v = reader.read(row.getCell(idx));
        if (v == null || v.displayValue() == null) return null;
        String s = v.displayValue().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isAllBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static List<String> limit(List<String> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }
}
