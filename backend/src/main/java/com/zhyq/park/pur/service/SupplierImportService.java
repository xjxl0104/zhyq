package com.zhyq.park.pur.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.importing.service.TabularFileReader;
import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.system.entity.SysDictData;
import com.zhyq.park.system.mapper.SysDictDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 供应商档案导入。文件格式解析见 {@link TabularFileReader}(Excel/CSV/TXT/docx 表格),按表头叫法认列。
 *
 * <p>类别列可以填字典 supplier_category 的中文名(物业服务)或值(property);
 * 字典里没有的类别原样保存,不拦截 —— 与手工建档口径一致(后端不做类别枚举校验)。</p>
 *
 * <p>去重:统一社会信用代码相同,或(无信用代码时)名称相同的已有供应商跳过,不覆盖已有档案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierImportService {

    private static final String DICT_TYPE = "supplier_category";

    /** 名称列的叫法,同时用来定位表头行 */
    static final List<String> NAME_HEADERS = List.of("供应商名称", "供应商", "单位名称", "公司名称", "企业名称", "名称");

    private static final Map<List<String>, BiConsumer<Supplier, String>> COLUMN_MAP = new LinkedHashMap<>();

    static {
        COLUMN_MAP.put(NAME_HEADERS, Supplier::setName);
        COLUMN_MAP.put(List.of("统一社会信用代码", "信用代码", "社会信用代码", "税号", "纳税人识别号"), Supplier::setCreditCode);
        COLUMN_MAP.put(List.of("法定代表人", "法人", "法人代表"), Supplier::setLegalPerson);
        COLUMN_MAP.put(List.of("联系人", "对接人", "联系人姓名"), Supplier::setContact);
        COLUMN_MAP.put(List.of("联系电话", "电话", "手机", "手机号", "联系方式"), Supplier::setPhone);
        COLUMN_MAP.put(List.of("邮箱", "电子邮箱", "Email", "email", "E-mail"), Supplier::setEmail);
        COLUMN_MAP.put(List.of("注册地址", "地址", "公司地址", "办公地址"), Supplier::setRegAddress);
        COLUMN_MAP.put(List.of("开户行", "开户银行", "银行"), Supplier::setBankName);
        COLUMN_MAP.put(List.of("银行账号", "账号", "开户账号", "银行帐号"), Supplier::setBankAccount);
        COLUMN_MAP.put(List.of("经营范围", "业务范围", "服务范围", "主营业务"), Supplier::setBusinessScope);
        COLUMN_MAP.put(List.of("资质说明", "资质", "资质证书"), Supplier::setQualification);
        COLUMN_MAP.put(List.of("备注", "说明"), Supplier::setRemark);
    }

    private static final List<String> CATEGORY_HEADERS = List.of("类别", "供应商类别", "类型", "分类");
    private static final List<String> STATUS_HEADERS = List.of("状态", "合作状态");

    private final SupplierMapper supplierMapper;
    private final SysDictDataMapper dictDataMapper;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        TabularFileReader.Table table = TabularFileReader.read(file, NAME_HEADERS, "「供应商名称」或「公司名称」");
        Map<String, String> categoryByLabel = loadCategories();

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<List<String>> rows = table.rows();
        for (int r = table.headerRow() + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Supplier s = new Supplier();
            for (Map.Entry<List<String>, BiConsumer<Supplier, String>> e : COLUMN_MAP.entrySet()) {
                String v = table.value(row, e.getKey());
                if (!v.isBlank()) {
                    e.getValue().accept(s, v);
                }
            }
            if (s.getName() == null || s.getName().isBlank()) {
                continue; // 空行
            }
            String category = table.value(row, CATEGORY_HEADERS);
            if (!category.isBlank()) {
                s.setCategory(categoryByLabel.getOrDefault(category, category));
            }
            s.setStatus(parseStatus(table.value(row, STATUS_HEADERS)));

            if (exists(s)) {
                skipped++;
                continue;
            }
            try {
                s.setCode(supplierMapper.nextCode());
                supplierMapper.insert(s);
                imported++;
            } catch (DuplicateKeyException ex) {
                log.warn("[pur] 供应商导入第 {} 行编号冲突", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + s.getName() + "」编号冲突,请重新导入");
            } catch (Exception ex) {
                log.warn("[pur] 供应商导入第 {} 行失败", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + s.getName() + "」保存失败,请检查该行内容(如文字过长)");
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    /** 字典中文名与值都映射到值,两种写法都认 */
    private Map<String, String> loadCategories() {
        Map<String, String> map = new HashMap<>();
        for (SysDictData d : dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, DICT_TYPE))) {
            if (d.getValue() == null) continue;
            map.put(d.getValue(), d.getValue());
            if (d.getLabel() != null) map.put(d.getLabel().trim(), d.getValue());
        }
        return map;
    }

    private boolean exists(Supplier s) {
        LambdaQueryWrapper<Supplier> q = new LambdaQueryWrapper<>();
        if (s.getCreditCode() != null && !s.getCreditCode().isBlank()) {
            q.eq(Supplier::getCreditCode, s.getCreditCode()).or().eq(Supplier::getName, s.getName());
        } else {
            q.eq(Supplier::getName, s.getName());
        }
        return supplierMapper.selectCount(q) > 0;
    }

    /** 1正常 2停用 3已归档;留空或无法识别按正常 */
    static int parseStatus(String v) {
        if (v == null) return 1;
        if (v.contains("归档")) return 3;
        if (v.contains("停")) return 2;
        return 1;
    }
}
