package com.zhyq.park.energy.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.service.FeeNoticeCalculator.SheetRow;
import com.zhyq.park.energy.service.FeeNoticeService.GarbageRow;
import com.zhyq.park.energy.service.FeeNoticeService.GarbageSheet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 把一个账期导出成与线下《租户对账版 应收收费明细表》同结构的工作簿:
 * 「应收收费明细表」(费用项目 × 租户矩阵) + 「电表汇总」「水表汇总」「垃圾分摊」+ 每户一张「缴费通知单」。
 */
@Service
public class FeeNoticeExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.M.d");
    private static final DateTimeFormatter DATE_CN = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final String[] NOTICE_HEADERS = {"序号", "费用项目", "计费期间", "上期读数", "本期读数", "计费数量", "单位", "单价（元）", "金额（元）", "备注"};
    private static final String[] SUMMARY_ROWS = {"水费", "公摊水费", "电费", "公摊电费", "生活垃圾清运费", "工业垃圾清运费", "其他费用"};
    private static final String[] NOTES = {
            "1. 水费、电费计费数量＝本期读数－上期读数；如存在倍率、损耗或公摊，请在备注中说明。",
            "2. 工业垃圾清运费应按租赁合同、园区收费标准或实际清运凭据填写。",
            "3. 本明细表为费用核对及收款依据之一；税务发票或收据的开具方式，以双方合同约定及适用税务规定为准。"};

    private record Styles(CellStyle title, CellStyle header, CellStyle text, CellStyle textWrap, CellStyle money,
                          CellStyle number, CellStyle bold, CellStyle label) {}

    public byte[] export(FeePeriod period, List<FeeNotice> notices, FeeNoticeService.Sheet electric,
                         FeeNoticeService.Sheet water, GarbageSheet garbage) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = styles(wb);
            writeSummary(wb.createSheet("应收收费明细表"), st, period, notices);
            writeUtilitySheet(wb.createSheet("电表汇总"), st, period, electric, "度", "电表");
            writeUtilitySheet(wb.createSheet("水表汇总"), st, period, water, "m³", "水表");
            writeGarbage(wb.createSheet("垃圾分摊"), st, period, garbage);
            Set<String> used = new HashSet<>();
            for (FeeNotice n : notices) {
                writeNotice(wb.createSheet(sheetName(n, used)), st, period, n);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException("缴费通知单导出失败");
        }
    }

    // ------------------------------------------------------------ 应收收费明细表

    private void writeSummary(Sheet sheet, Styles st, FeePeriod p, List<FeeNotice> notices) {
        int cols = 3 + notices.size() + 1;
        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        cell(title, 0, p.getPeriod() + " " + nz(p.getParkName()) + "应收收费明细表", st.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(cols - 1, 4)));

        Row h1 = sheet.createRow(1);
        Row h2 = sheet.createRow(2);
        h1.setHeightInPoints(34);
        cell(h1, 0, "序号", st.header());
        cell(h1, 1, "费用项目", st.header());
        cell(h1, 2, "计费期间", st.header());
        cell(h2, 0, "", st.header());
        cell(h2, 1, "", st.header());
        cell(h2, 2, "", st.header());
        for (int i = 0; i < notices.size(); i++) {
            FeeNotice n = notices.get(i);
            cell(h1, 3 + i, shortName(n) + "\n（" + nz(n.getUnitName()) + "）", st.header());
            cell(h2, 3 + i, "金额/元", st.header());
        }
        cell(h1, cols - 1, "合计", st.header());
        cell(h2, cols - 1, "合计金额/元", st.header());

        String range = periodRange(p);
        List<Function<FeeNotice, BigDecimal>> getters = List.of(
                FeeNotice::getWaterFee, FeeNotice::getWaterPublicFee, FeeNotice::getElectricFee,
                FeeNotice::getElectricPublicFee, FeeNotice::getDomesticGarbageFee,
                FeeNotice::getIndustrialGarbageFee, FeeNotice::getOtherFee);
        int r = 3;
        for (int i = 0; i < SUMMARY_ROWS.length; i++) {
            r = summaryLine(sheet, st, r, String.valueOf(i + 1), SUMMARY_ROWS[i], range, notices, getters.get(i));
        }
        r = summaryLine(sheet, st, r, "一", "费用小计", range, notices, FeeNotice::getSubtotal);
        r = summaryLine(sheet, st, r, "二", "已付金额", range, notices, FeeNotice::getPaidAmount);
        r = summaryLine(sheet, st, r, "三", "前期欠费", range, notices, FeeNotice::getPriorArrears);
        r = summaryLine(sheet, st, r, "四", "本期应收合计", range, notices, FeeNotice::getTotalDue);

        r++;
        info(sheet, st, r++, "联系人", nz(p.getContactName()));
        info(sheet, st, r++, "联系电话", nz(p.getContactPhone()));
        info(sheet, st, r++, "开具日期", p.getIssueDate() == null ? "" : p.getIssueDate().format(DATE_CN));
        info(sheet, st, r++, "缴费截止日", p.getDueDate() == null ? "" : p.getDueDate().format(DATE_CN));
        info(sheet, st, r, "收款账户/方式", nz(p.getPaymentAccount()));
        sheet.getRow(r).setHeightInPoints(16 * Math.max(1, nz(p.getPaymentAccount()).split("\n").length));

        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        for (int i = 3; i < cols; i++) {
            sheet.setColumnWidth(i, 16 * 256);
        }
        sheet.createFreezePane(3, 3);
    }

    private int summaryLine(Sheet sheet, Styles st, int rowNo, String seq, String label, String range,
                            List<FeeNotice> notices, Function<FeeNotice, BigDecimal> getter) {
        Row row = sheet.createRow(rowNo);
        boolean total = label.contains("合计") || label.contains("小计");
        cell(row, 0, seq, st.text());
        cell(row, 1, label, total ? st.bold() : st.text());
        cell(row, 2, range, st.text());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < notices.size(); i++) {
            BigDecimal v = nz(getter.apply(notices.get(i)));
            sum = sum.add(v);
            money(row, 3 + i, v, st.money());
        }
        money(row, 3 + notices.size(), sum, st.money());
        return rowNo + 1;
    }

    private void info(Sheet sheet, Styles st, int rowNo, String label, String value) {
        Row row = sheet.createRow(rowNo);
        cell(row, 1, label, st.label());
        cell(row, 2, value, st.textWrap());
    }

    // ------------------------------------------------------------ 电表 / 水表汇总

    private void writeUtilitySheet(Sheet sheet, Styles st, FeePeriod p, FeeNoticeService.Sheet data,
                                   String unit, String meterLabel) {
        Row title = sheet.createRow(0);
        title.setHeightInPoints(26);
        cell(title, 0, nz(p.getParkName()) + p.getPeriod() + meterLabel + "汇总", st.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 13));

        int r = 1;
        if ("电表".equals(meterLabel)) {
            r = kv(sheet, st, r, "南网用电总度数", nz(p.getElectricGridUsage()), "光伏发电园区用电", nz(p.getElectricSolarUsage()));
            r = kv(sheet, st, r, "总度数(南网+光伏)", nz(data.total()), "全部分表用量", data.submeterUsage());
        } else {
            r = kv(sheet, st, r, "总表度数(" + data.totalSource() + ")", nz(data.total()), "全部分表用量", data.submeterUsage());
        }
        r = kv(sheet, st, r, "公摊度数", data.publicUsage(), "参与公摊租户用量", data.shareBase());
        r = kv(sheet, st, r, "单价(元/" + unit + ")", data.price(), "租户应收合计(元)", data.totalFee());
        r++;

        String[] heads = {"序号", "区域", "表计编号", "用户名", "租户(合同)", "角色", "参与公摊", "倍率", "上期读数", "本期读数",
                "本期用量", "公摊比例", "公摊度数", "度数+公摊", "单价(元)", "合计/元"};
        Row h = sheet.createRow(r++);
        for (int i = 0; i < heads.length; i++) {
            cell(h, i, heads[i], st.header());
        }
        int idx = 1;
        for (SheetRow row : data.rows()) {
            Row x = sheet.createRow(r++);
            cell(x, 0, String.valueOf(idx++), st.text());
            cell(x, 1, nz(row.getArea()), st.text());
            cell(x, 2, nz(row.getCode()), st.text());
            cell(x, 3, nz(row.getCustomerName()), st.text());
            cell(x, 4, nz(row.getTenantName()), st.text());
            cell(x, 5, "PROPERTY".equals(row.getMeterRole()) ? "物业公司表" : "租户分表", st.text());
            cell(x, 6, row.isSharePublic() ? "是" : "否", st.text());
            number(x, 7, row.getRatio(), st.number());
            number(x, 8, row.getPrevReading(), st.number());
            number(x, 9, row.getCurrReading(), st.number());
            number(x, 10, row.getUsage(), st.number());
            number(x, 11, row.getCoefficient(), st.number());
            number(x, 12, row.getPublicUsage(), st.number());
            number(x, 13, row.getTotalUsage(), st.number());
            number(x, 14, data.price(), st.number());
            money(x, 15, row.getTotalFee(), st.money());
        }
        Row total = sheet.createRow(r);
        cell(total, 1, "合计", st.bold());
        number(total, 10, data.submeterUsage(), st.number());
        number(total, 12, data.publicUsage(), st.number());
        number(total, 13, data.submeterUsage().add(data.publicUsage()), st.number());
        money(total, 15, data.totalFee(), st.money());
        for (int i = 0; i < heads.length; i++) {
            sheet.setColumnWidth(i, (i == 1 || i == 3 || i == 4 ? 16 : 12) * 256);
        }
    }

    private int kv(Sheet sheet, Styles st, int rowNo, String k1, BigDecimal v1, String k2, BigDecimal v2) {
        Row row = sheet.createRow(rowNo);
        cell(row, 0, k1, st.label());
        sheet.addMergedRegion(new CellRangeAddress(rowNo, rowNo, 0, 2));
        number(row, 3, v1, st.number());
        cell(row, 5, k2, st.label());
        sheet.addMergedRegion(new CellRangeAddress(rowNo, rowNo, 5, 7));
        number(row, 8, v2, st.number());
        return rowNo + 1;
    }

    // ------------------------------------------------------------ 垃圾分摊

    private void writeGarbage(Sheet sheet, Styles st, FeePeriod p, GarbageSheet g) {
        Row title = sheet.createRow(0);
        title.setHeightInPoints(26);
        cell(title, 0, nz(p.getParkName()) + p.getPeriod() + "垃圾清运费分摊表", st.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        int r = 1;
        r = kv(sheet, st, r, "生活垃圾总额(元)", nz(p.getDomesticGarbageAmount()), "工业垃圾总额(元)", nz(p.getIndustrialGarbageAmount()));
        Row note = sheet.createRow(r++);
        cell(note, 0, "说明", st.label());
        cell(note, 3, nz(p.getDomesticGarbageNote()) + (p.getIndustrialGarbageNote() == null ? "" : "；" + p.getIndustrialGarbageNote()), st.text());
        r = kv(sheet, st, r, "参与分摊计租面积合计(㎡)", g.totalLeaseArea(), "", null);
        r++;
        String[] heads = {"序号", "区域", "用户名", "参与分摊", "计租面积(㎡)", "计租/总计租面积", "生活垃圾清运费", "工业垃圾清运费", "合计/元"};
        Row h = sheet.createRow(r++);
        for (int i = 0; i < heads.length; i++) {
            cell(h, i, heads[i], st.header());
        }
        int idx = 1;
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal ind = BigDecimal.ZERO;
        for (GarbageRow row : g.rows()) {
            Row x = sheet.createRow(r++);
            cell(x, 0, String.valueOf(idx++), st.text());
            cell(x, 1, row.unit(), st.text());
            cell(x, 2, nz(row.tenantName()), st.text());
            cell(x, 3, row.sharePublic() ? "是" : "否", st.text());
            number(x, 4, row.leaseArea(), st.number());
            number(x, 5, row.ratio(), st.number());
            money(x, 6, row.domesticFee(), st.money());
            money(x, 7, row.industrialFee(), st.money());
            money(x, 8, nz(row.domesticFee()).add(nz(row.industrialFee())), st.money());
            d = d.add(nz(row.domesticFee()));
            ind = ind.add(nz(row.industrialFee()));
        }
        Row total = sheet.createRow(r);
        cell(total, 1, "合计", st.bold());
        number(total, 4, g.totalLeaseArea(), st.number());
        money(total, 6, d, st.money());
        money(total, 7, ind, st.money());
        money(total, 8, d.add(ind), st.money());
        for (int i = 0; i < heads.length; i++) {
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    // ------------------------------------------------------------ 缴费通知单(每户一张)

    private void writeNotice(Sheet sheet, Styles st, FeePeriod p, FeeNotice n) {
        int last = NOTICE_HEADERS.length - 1;
        Row title = sheet.createRow(0);
        title.setHeightInPoints(30);
        cell(title, 0, nz(p.getParkName()) + "缴费通知单", st.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, last));
        Row sub = sheet.createRow(1);
        cell(sub, 0, "本表用于向租户列明水电费及垃圾清运费；", st.text());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, last));

        String range = periodRange(p);
        headerLine(sheet, st, 3, "园区名称", nz(p.getParkName()), "租户/公司名称", nz(n.getTenantName()), "单据编号", nz(n.getNoticeNo()));
        headerLine(sheet, st, 4, "厂房/单元", nz(n.getUnitName()), "收费期间", range, "缴费截止日",
                p.getDueDate() == null ? "" : p.getDueDate().format(DATE_CN));
        headerLine(sheet, st, 5, "联系人", nz(p.getContactName()), "联系电话", nz(p.getContactPhone()), "收款账户/方式", nz(p.getPaymentAccount()));
        sheet.getRow(5).setHeightInPoints(16 * Math.max(1, nz(p.getPaymentAccount()).split("\n").length));
        headerLine(sheet, st, 6, "开具日期", p.getIssueDate() == null ? "" : p.getIssueDate().format(DATE_CN), "备注", nz(n.getRemark()), "", "");

        int r = 8;
        Row h = sheet.createRow(r++);
        for (int i = 0; i < NOTICE_HEADERS.length; i++) {
            cell(h, i, NOTICE_HEADERS[i], st.header());
        }
        for (FeeNoticeItem it : n.getItems()) {
            Row x = sheet.createRow(r++);
            cell(x, 0, nz(it.getSeq()), st.text());
            cell(x, 1, (it.isDetail() ? "　" : "") + nz(it.getFeeItem()), st.text());
            cell(x, 2, range, st.text());
            number(x, 3, it.getPrevReading(), st.number());
            number(x, 4, it.getCurrReading(), st.number());
            number(x, 5, it.getQuantity(), st.number());
            cell(x, 6, nz(it.getUnit()), st.text());
            number(x, 7, it.getUnitPrice(), st.number());
            if (it.isDetail()) {
                cell(x, 8, "", st.text());
            } else {
                money(x, 8, it.getAmount(), st.money());
            }
            cell(x, 9, nz(it.getRemark()), st.textWrap());
        }
        r++;
        int noteStart = r;
        footer(sheet, st, r++, "收费说明", "费用小计", n.getSubtotal(), "本期应收合计");
        footer(sheet, st, r++, NOTES[0], "已付金额", n.getPaidAmount(), "-");
        footer(sheet, st, r++, NOTES[1], "前期欠费", n.getPriorArrears(), nz(n.getPriorArrears()).signum() == 0 ? "无" : "");
        footer(sheet, st, r++, NOTES[2], "本期应收合计", n.getTotalDue(), "");
        for (int i = noteStart; i < r; i++) {
            sheet.addMergedRegion(new CellRangeAddress(i, i, 0, 6));
        }
        r++;
        Row sign = sheet.createRow(r);
        sign.setHeightInPoints(36);
        cell(sign, 0, "园区方确认（签字/盖章）：", st.text());
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 4));
        cell(sign, 5, "租户确认（签字/盖章）：", st.text());
        sheet.addMergedRegion(new CellRangeAddress(r, r, 5, last));

        int[] widths = {7, 18, 22, 11, 11, 11, 7, 11, 13, 46};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
    }

    private void headerLine(Sheet sheet, Styles st, int rowNo, String k1, String v1, String k2, String v2, String k3, String v3) {
        Row row = sheet.createRow(rowNo);
        cell(row, 0, k1, st.label());
        cell(row, 1, v1, st.textWrap());
        sheet.addMergedRegion(new CellRangeAddress(rowNo, rowNo, 1, 2));
        cell(row, 3, k2, st.label());
        cell(row, 4, v2, st.textWrap());
        sheet.addMergedRegion(new CellRangeAddress(rowNo, rowNo, 4, 5));
        cell(row, 6, k3, st.label());
        cell(row, 7, v3, st.textWrap());
        sheet.addMergedRegion(new CellRangeAddress(rowNo, rowNo, 7, 9));
    }

    private void footer(Sheet sheet, Styles st, int rowNo, String note, String label, BigDecimal amount, String tail) {
        Row row = sheet.createRow(rowNo);
        cell(row, 0, note, st.textWrap());
        cell(row, 7, label, st.label());
        money(row, 8, amount, st.money());
        cell(row, 9, tail, st.text());
    }

    // ------------------------------------------------------------ 小工具

    private static String sheetName(FeeNotice n, Set<String> used) {
        String base = (shortName(n) + "（" + nz(n.getUnitName()) + "）").replaceAll("[\\\\/?*\\[\\]:]", "");
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String name = base;
        int i = 2;
        while (!used.add(name)) {
            name = base + i++;
        }
        return name;
    }

    /** 公司全称太长,表头用去掉「广州/广东/有限公司」之类后的简称 */
    private static String shortName(FeeNotice n) {
        String name = nz(n.getTenantName());
        String s = name.replaceAll("(有限公司|股份有限公司|有限责任公司|公司)$", "")
                .replaceAll("^(广州|广东|深圳|佛山|中国)", "")
                .replaceAll("[（(].*?[)）]", "");
        if (s.length() > 6) {
            s = s.substring(0, 6);
        }
        return s.isBlank() ? name : s;
    }

    private static String periodRange(FeePeriod p) {
        LocalDate s = p.getPeriodStart();
        LocalDate e = p.getPeriodEnd();
        if (s == null || e == null) {
            return nz(p.getPeriod());
        }
        return s.format(DATE) + "~" + e.format(DATE);
    }

    private static Styles styles(Workbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);

        CellStyle title = wb.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle header = bordered(wb);
        header.setFont(bold);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setWrapText(true);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle text = bordered(wb);
        CellStyle textWrap = bordered(wb);
        textWrap.setWrapText(true);
        CellStyle label = bordered(wb);
        label.setFont(bold);
        label.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle boldCell = bordered(wb);
        boldCell.setFont(bold);

        CellStyle money = bordered(wb);
        money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        money.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle number = bordered(wb);
        number.setDataFormat(wb.createDataFormat().getFormat("0.##"));
        number.setAlignment(HorizontalAlignment.RIGHT);
        return new Styles(title, header, text, textWrap, money, number, boldCell, label);
    }

    private static CellStyle bordered(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void money(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(nz(value).doubleValue());
        c.setCellStyle(style);
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value.doubleValue());
        }
        c.setCellStyle(style);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    /** 保留给后续按费用项目做横向汇总时复用 */
    @SuppressWarnings("unused")
    private static Map<String, BigDecimal> emptyTotals() {
        return new LinkedHashMap<>();
    }
}
