package com.zhyq.park.crm.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 《云仓产业园客户信息收集与回访登记表》导入。
 *
 * <p>按**表头文字**认列,不依赖列顺序 —— 招商同事常在表里插列,写死下标下次就错位。
 * 表头行自动探测(前 5 行里找到含「客户姓名」的那一行),因为实际文件有时上面还压着标题行。</p>
 *
 * <p>只导入「客户信息登记表」这一张 sheet;回访记录的导入需要先按客户编号对齐线索,
 * 逻辑与人工核对相关,本次不做。</p>
 *
 * <p>去重:同一「客户姓名 + 联系电话」已存在则跳过并计入 skipped,重复导入同一份文件不会翻倍。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadImportService {

    /** 目标 sheet 名;找不到就退回第一张有数据的 sheet */
    private static final String SHEET_NAME = "客户信息登记表";
    /** 表头必含这一列,用来定位表头行 */
    private static final String ANCHOR_HEADER = "客户姓名";
    private static final int HEADER_SCAN_ROWS = 5;

    /** 表头文字 → Lead 字段的 setter。key 用去空格后的表头 */
    private static final Map<String, java.util.function.BiConsumer<Lead, String>> COLUMN_MAP = new LinkedHashMap<>();

    static {
        COLUMN_MAP.put("客户来源", Lead::setSource);
        COLUMN_MAP.put("客户姓名", Lead::setContact);
        COLUMN_MAP.put("联系电话", Lead::setPhone);
        COLUMN_MAP.put("公司名称/店铺名称", Lead::setCompany);
        COLUMN_MAP.put("客户类型", Lead::setCustomerType);
        COLUMN_MAP.put("意向合作方式", Lead::setCoopMode);
        COLUMN_MAP.put("仓库面积/库容需求(㎡)", Lead::setDemandArea);
        COLUMN_MAP.put("主营品类/货物类型", Lead::setGoodsType);
        COLUMN_MAP.put("日均单量/月发货量", Lead::setOrderVolume);
        COLUMN_MAP.put("意向合作周期", Lead::setCoopPeriod);
        COLUMN_MAP.put("预算/租金心理价位", Lead::setBudgetPrice);
        COLUMN_MAP.put("意向园区或对接仓库", Lead::setIntentPark);
        COLUMN_MAP.put("客户所在地区", Lead::setRegion);
        COLUMN_MAP.put("客户等级", Lead::setGrade);
        COLUMN_MAP.put("跟进负责人", Lead::setOwnerName);
        COLUMN_MAP.put("跟进记录", Lead::setRemark);
    }

    /** 登记表「当前状态」文字 → 状态码 */
    private static final Map<String, Integer> STATUS_MAP = Map.of(
            "待跟进", LeadService.ST_PENDING,
            "跟进中", LeadService.ST_FOLLOWING,
            "已约看仓/已对接", LeadService.ST_VISITED,
            "已报价/洽谈中", LeadService.ST_QUOTED,
            "已签约/已成交", LeadService.ST_SIGNED,
            "已流失/暂缓", LeadService.ST_LOST);

    private final LeadMapper leadMapper;
    private final LeadService leadService;

    /** 导入结果:成功、跳过(重复)、失败(带行号与原因) */
    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            }
            if (sheet == null) {
                throw new BizException("文件里没有可读的工作表");
            }

            int headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                throw new BizException("没找到表头行(应含「" + ANCHOR_HEADER + "」列),请确认用的是客户信息登记表");
            }
            Map<String, Integer> colIndex = readHeader(sheet.getRow(headerRow));

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Lead lead = new Lead();
                for (Map.Entry<String, java.util.function.BiConsumer<Lead, String>> e : COLUMN_MAP.entrySet()) {
                    Integer idx = colIndex.get(e.getKey());
                    if (idx != null) {
                        String v = cellText(row.getCell(idx));
                        if (!v.isBlank()) {
                            e.getValue().accept(lead, v);
                        }
                    }
                }
                // 姓名为空视为空行,静默跳过(登记表尾部有大量预留空行)
                if (lead.getContact() == null || lead.getContact().isBlank()) {
                    continue;
                }
                lead.setRegisterDate(cellDate(row, colIndex.get("登记日期")));
                lead.setStatus(STATUS_MAP.getOrDefault(
                        cellText(row.getCell(colIndex.getOrDefault("当前状态", -1))), LeadService.ST_PENDING));

                if (exists(lead.getContact(), lead.getPhone())) {
                    skipped++;
                    continue;
                }
                try {
                    leadService.create(lead);
                    imported++;
                } catch (Exception ex) {
                    // 单行失败不拖垮整批,记录行号让用户能定位
                    errors.add("第 " + (r + 1) + " 行:" + ex.getMessage());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[crm] 线索导入解析失败", e);
            throw new BizException("文件解析失败,请确认是 .xlsx / .xls 格式的登记表");
        }
        return new ImportResult(imported, skipped, errors);
    }

    /** 前若干行里找含锚点表头的那一行,兼容表头上方有标题行的情况 */
    private int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                if (ANCHOR_HEADER.equals(normalize(cellText(c)))) {
                    return r;
                }
            }
        }
        return -1;
    }

    private Map<String, Integer> readHeader(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell c : header) {
            String text = normalize(cellText(c));
            if (!text.isEmpty()) {
                map.putIfAbsent(text, c.getColumnIndex());
            }
        }
        return map;
    }

    private LocalDate cellDate(Row row, Integer idx) {
        if (idx == null) {
            return null;
        }
        Cell c = row.getCell(idx);
        if (c == null) {
            return null;
        }
        try {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = cellText(c);
            return s.isBlank() ? null : LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (Exception e) {
            return null; // 日期格式五花八门,解析不了就留空,不因此让整行失败
        }
    }

    private boolean exists(String contact, String phone) {
        return leadMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Lead>()
                .eq(Lead::getContact, contact)
                .eq(phone != null && !phone.isBlank(), Lead::getPhone, phone)) > 0;
    }

    /** 表头可能带换行/空格,统一去掉再比对 */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00a0]", "");
    }

    /** 单元格取文本:数字去掉多余小数尾巴,日期转 ISO,其余按字符串 */
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
