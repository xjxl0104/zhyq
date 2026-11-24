package com.zhyq.park.energy.service;

import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeeNoticeItem;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.service.LeaseFeeCalculator.ContractMonthRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaseFeeExportService {

    public byte[] export(LeaseFeePeriod period, LeaseFeeService.Sheet sheet, List<LeaseFeeNotice> notices) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle titleStyle = makeTitleStyle(wb);
            CellStyle headStyle = makeHeadStyle(wb);
            CellStyle textStyle = makeCellStyle(wb, HorizontalAlignment.LEFT, false);
            CellStyle numStyle = makeCellStyle(wb, HorizontalAlignment.RIGHT, true);
            CellStyle totalStyle = makeTotalStyle(wb);

            buildTenantSummarySheet(wb, period, notices, titleStyle, headStyle, textStyle, numStyle, totalStyle);
            buildDetailSheet(wb, "租金明细", period, sheet.rows(), true, titleStyle, headStyle, textStyle, numStyle, totalStyle);
            buildDetailSheet(wb, "物业管理费明细", period, sheet.rows(), false, titleStyle, headStyle, textStyle, numStyle, totalStyle);
            buildNoticeListSheet(wb, "租金通知单明细", period,
                    notices.stream().filter(n -> LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory())).toList(),
                    period.getRentPayeeName(), period.getRentPaymentAccount(), titleStyle, headStyle, textStyle, numStyle);
            buildNoticeListSheet(wb, "物业费通知单明细", period,
                    notices.stream().filter(n -> LeaseFeeNotice.CAT_PROPERTY.equals(n.getFeeCategory())).toList(),
                    period.getPropertyPayeeName(), period.getPropertyPaymentAccount(), titleStyle, headStyle, textStyle, numStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出租金物业费 Excel 失败", e);
        }
    }

    private void buildTenantSummarySheet(XSSFWorkbook wb, LeaseFeePeriod p, List<LeaseFeeNotice> notices,
                                         CellStyle titleStyle, CellStyle headStyle, CellStyle textStyle,
                                         CellStyle numStyle, CellStyle totalStyle) {
        Sheet s = wb.createSheet("月度汇总-分租户");
        int r = 0;
        Row title = s.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue(nz(p.getParkName()) + " " + p.getPeriod() + " 租金与物业管理费月度汇总");
        tc.setCellStyle(titleStyle);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row payeeRow = s.createRow(r++);
        payeeRow.createCell(0).setCellValue("租金收取单位：" + nz(p.getRentPayeeName()) + " ｜ 物业管理费收取单位：" + nz(p.getPropertyPayeeName()));
        s.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

        String[] heads = {"序号", "租户名称", "楼层/区域", "计租面积(㎡)", "应付租金(元)", "应付物业管理费(元)", "应付合计(元)"};
        Row hr = s.createRow(r++);
        for (int i = 0; i < heads.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(headStyle);
        }

        Map<String, LeaseFeeNotice> rentByTenant = new LinkedHashMap<>();
        Map<String, LeaseFeeNotice> propByTenant = new LinkedHashMap<>();
        for (LeaseFeeNotice n : notices) {
            if (LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory())) rentByTenant.put(n.getTenantName(), n);
            else propByTenant.put(n.getTenantName(), n);
        }
        Map<String, Boolean> tenants = new LinkedHashMap<>();
        rentByTenant.keySet().forEach(k -> tenants.put(k, true));
        propByTenant.keySet().forEach(k -> tenants.put(k, true));

        int idx = 0;
        BigDecimal sumArea = BigDecimal.ZERO, sumRent = BigDecimal.ZERO, sumProp = BigDecimal.ZERO;
        for (String tenant : tenants.keySet()) {
            idx++;
            LeaseFeeNotice rn = rentByTenant.get(tenant);
            LeaseFeeNotice pn = propByTenant.get(tenant);
            LeaseFeeNotice ref = rn != null ? rn : pn;
            BigDecimal area = nzDec(ref.getLeaseArea());
            BigDecimal rent = rn == null ? BigDecimal.ZERO : nzDec(rn.getTotalDue());
            BigDecimal prop = pn == null ? BigDecimal.ZERO : nzDec(pn.getTotalDue());
            sumArea = sumArea.add(area);
            sumRent = sumRent.add(rent);
            sumProp = sumProp.add(prop);

            Row row = s.createRow(r++);
            setText(row, 0, String.valueOf(idx), textStyle);
            setText(row, 1, tenant, textStyle);
            setText(row, 2, nz(ref.getUnitName()), textStyle);
            setNum(row, 3, area, numStyle);
            setNum(row, 4, rent, numStyle);
            setNum(row, 5, prop, numStyle);
            setNum(row, 6, rent.add(prop), numStyle);
        }

        Row tr = s.createRow(r);
        setText(tr, 0, "合计", totalStyle);
        setText(tr, 1, "", totalStyle);
        setText(tr, 2, "", totalStyle);
        setNum(tr, 3, sumArea, totalStyle);
        setNum(tr, 4, sumRent, totalStyle);
        setNum(tr, 5, sumProp, totalStyle);
        setNum(tr, 6, sumRent.add(sumProp), totalStyle);

        int[] widths = {8, 30, 34, 15, 16, 18, 16};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i] * 256);
    }

    private void buildDetailSheet(XSSFWorkbook wb, String name, LeaseFeePeriod p, List<ContractMonthRow> rows,
                                  boolean isRent, CellStyle titleStyle, CellStyle headStyle, CellStyle textStyle,
                                  CellStyle numStyle, CellStyle totalStyle) {
        Sheet s = wb.createSheet(name);
        int r = 0;
        Row title = s.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue(nz(p.getParkName()) + " " + p.getPeriod() + " " + name + "（收取单位："
                + nz(isRent ? p.getRentPayeeName() : p.getPropertyPayeeName()) + "）");
        tc.setCellStyle(titleStyle);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        String[] heads = {"序号", "租户名称", "楼层/区域", "计租面积(㎡)",
                isRent ? "当期租金单价" : "当期物业费单价",
                isRent ? "标准租金(元)" : "标准物业费(元)",
                "减免系数", isRent ? "应付租金(元)" : "应付物业费(元)", "备注"};
        Row hr = s.createRow(r++);
        for (int i = 0; i < heads.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(headStyle);
        }

        int idx = 0;
        BigDecimal sumArea = BigDecimal.ZERO, sumStd = BigDecimal.ZERO, sumPay = BigDecimal.ZERO;
        for (ContractMonthRow rowData : rows) {
            idx++;
            BigDecimal area = nzDec(rowData.getLeaseArea());
            BigDecimal price = isRent ? nzDec(rowData.getRentUnitPrice()) : nzDec(rowData.getPropertyUnitPrice());
            BigDecimal std = isRent ? nzDec(rowData.getStandardRent()) : nzDec(rowData.getStandardProperty());
            BigDecimal factor = isRent ? nzDec(rowData.getRentFactor()) : nzDec(rowData.getPropertyFactor());
            BigDecimal pay = isRent ? nzDec(rowData.getPayableRent()) : nzDec(rowData.getPayableProperty());
            sumArea = sumArea.add(area);
            sumStd = sumStd.add(std);
            sumPay = sumPay.add(pay);

            Row row = s.createRow(r++);
            setText(row, 0, String.valueOf(idx), textStyle);
            setText(row, 1, nz(rowData.getTenantName()), textStyle);
            setText(row, 2, nz(rowData.getUnitName()), textStyle);
            setNum(row, 3, area, numStyle);
            setNum(row, 4, price, numStyle);
            setNum(row, 5, std, numStyle);
            setNum(row, 6, factor, numStyle);
            setNum(row, 7, pay, numStyle);
            setText(row, 8, nz(rowData.getRemark()), textStyle);
        }

        Row tr = s.createRow(r);
        setText(tr, 0, "合计", totalStyle);
        setText(tr, 1, "", totalStyle);
        setText(tr, 2, "", totalStyle);
        setNum(tr, 3, sumArea, totalStyle);
        setText(tr, 4, "", totalStyle);
        setNum(tr, 5, sumStd, totalStyle);
        setText(tr, 6, "", totalStyle);
        setNum(tr, 7, sumPay, totalStyle);
        setText(tr, 8, "", totalStyle);

        int[] widths = {8, 28, 34, 14, 15, 16, 12, 16, 36};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i] * 256);
    }

    private void buildNoticeListSheet(XSSFWorkbook wb, String name, LeaseFeePeriod p, List<LeaseFeeNotice> notices,
                                      String payeeName, String paymentAccount,
                                      CellStyle titleStyle, CellStyle headStyle, CellStyle textStyle, CellStyle numStyle) {
        Sheet s = wb.createSheet(name);
        int r = 0;
        Row title = s.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue(nz(p.getParkName()) + " " + p.getPeriod() + " " + name + " — 收取单位：" + nz(payeeName));
        tc.setCellStyle(titleStyle);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        if (paymentAccount != null && !paymentAccount.isBlank()) {
            Row acc = s.createRow(r++);
            acc.createCell(0).setCellValue("收款账户：" + paymentAccount.replace("\n", " "));
            s.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));
        }

        String[] heads = {"单据编号", "租户名称", "费用项目", "楼层/区域", "计租面积(㎡)", "单价(元/㎡/月)", "减免系数", "应付金额(元)", "备注"};
        Row hr = s.createRow(r++);
        for (int i = 0; i < heads.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(headStyle);
        }
        for (LeaseFeeNotice n : notices) {
            if (n.getItems() == null) continue;
            for (LeaseFeeNoticeItem it : n.getItems()) {
                Row row = s.createRow(r++);
                setText(row, 0, nz(n.getNoticeNo()), textStyle);
                setText(row, 1, nz(n.getTenantName()), textStyle);
                setText(row, 2, nz(it.getFeeItem()), textStyle);
                setText(row, 3, nz(it.getUnitName()), textStyle);
                setNum(row, 4, nzDec(it.getLeaseArea()), numStyle);
                setNum(row, 5, nzDec(it.getUnitPrice()), numStyle);
                setNum(row, 6, it.getDiscountFactor() == null ? BigDecimal.ONE : it.getDiscountFactor(), numStyle);
                setNum(row, 7, nzDec(it.getAmount()), numStyle);
                setText(row, 8, nz(it.getRemark()), textStyle);
            }
        }
        int[] widths = {24, 28, 16, 32, 14, 15, 12, 16, 32};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i] * 256);
    }

    private static void setText(Row r, int col, String v, CellStyle st) {
        Cell c = r.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(st);
    }

    private static void setNum(Row r, int col, BigDecimal v, CellStyle st) {
        Cell c = r.createCell(col);
        c.setCellValue(v == null ? 0 : v.doubleValue());
        c.setCellStyle(st);
    }

    private static CellStyle makeTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private static CellStyle makeHeadStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(s);
        return s;
    }

    private static CellStyle makeCellStyle(Workbook wb, HorizontalAlignment align, boolean num) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(align);
        if (num) s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        border(s);
        return s;
    }

    private static CellStyle makeTotalStyle(Workbook wb) {
        CellStyle s = makeCellStyle(wb, HorizontalAlignment.RIGHT, true);
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static BigDecimal nzDec(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
