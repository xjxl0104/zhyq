package com.zhyq.park.energy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.energy.entity.LeaseContract;
import com.zhyq.park.energy.entity.LeaseFeeEntry;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeeNoticeItem;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租金与物业管理费逐月计算引擎(纯计算,无数据库依赖,可直接单测)。
 *
 * <p>口径与《物流产业园租户租金/物业管理费逐月应付台账》一致:
 * 1. 递增:以起租日为基准,每满 escalationMonths(默认36月)复利上调 escalationRate;
 *    非1日起租的合同,递增当月按自然月天数加权计算单价;到期末月不足整月按天数折算。
 * 2. 免租/优惠/补贴/周期免租由合同 rulesJson 自动推算默认减免系数,可被月度手工调整覆盖。
 * 3. 租金与物业管理费因收取单位不同,分别按租户合并生成独立缴费通知单。</p>
 */
public final class LeaseFeeCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LeaseFeeCalculator() {}

    @Data
    public static class ContractMonthRow {
        private Long contractId;
        private Integer seqNo;
        private Long tenantRefId;
        private String tenantName;
        private String unitName;
        private String agreementNo;
        private String collectionTiming;
        private BigDecimal leaseArea;
        private BigDecimal rentUnitPrice;
        private BigDecimal propertyUnitPrice;
        private BigDecimal standardRent;
        private BigDecimal standardProperty;
        private BigDecimal defaultRentFactor;
        private BigDecimal defaultPropertyFactor;
        private BigDecimal rentFactor;
        private BigDecimal propertyFactor;
        private BigDecimal rentAmountOverride;
        private BigDecimal propertyAmountOverride;
        private BigDecimal payableRent;
        private BigDecimal payableProperty;
        private BigDecimal payableTotal;
        private String defaultRemark;
        private String remark;
        private boolean adjusted;
    }

    public record DefaultRuleResult(BigDecimal rentFactor, BigDecimal propertyFactor, String remark) {}

    public static List<ContractMonthRow> calculateMonth(YearMonth ym,
                                                        List<LeaseContract> contracts,
                                                        Map<Long, LeaseFeeEntry> entryByContract) {
        List<ContractMonthRow> rows = new ArrayList<>();
        if (contracts == null || contracts.isEmpty()) {
            return rows;
        }
        for (LeaseContract c : contracts) {
            if (c.getStartDate() == null || c.getEndDate() == null) {
                continue;
            }
            YearMonth startYm = YearMonth.from(c.getStartDate());
            YearMonth endYm = YearMonth.from(c.getEndDate());
            if (ym.isBefore(startYm) || ym.isAfter(endYm)) {
                continue;
            }
            LeaseFeeEntry entry = entryByContract == null ? null : entryByContract.get(c.getId());
            rows.add(calculateContractRow(ym, c, entry));
        }
        return rows;
    }

    public static ContractMonthRow calculateContractRow(YearMonth ym, LeaseContract c, LeaseFeeEntry entry) {
        YearMonth startYm = YearMonth.from(c.getStartDate());
        YearMonth endYm = YearMonth.from(c.getEndDate());
        int monthsElapsed = (int) ChronoUnit.MONTHS.between(startYm, ym);
        int escMonths = (c.getEscalationMonths() == null || c.getEscalationMonths() <= 0) ? 36 : c.getEscalationMonths();
        BigDecimal escRate = nz(c.getEscalationRate());
        int cycle = monthsElapsed / escMonths;

        BigDecimal onePlusRate = BigDecimal.ONE.add(escRate, MC);
        BigDecimal factorCurr = onePlusRate.pow(cycle, MC);
        BigDecimal multiplier = factorCurr;
        String escalationNote = null;

        int startDay = c.getStartDate().getDayOfMonth();
        if (cycle > 0 && monthsElapsed % escMonths == 0 && startDay > 1 && escRate.signum() != 0) {
            int daysInMonth = ym.lengthOfMonth();
            int oldDays = startDay - 1;
            int newDays = daysInMonth - oldDays;
            BigDecimal factorPrev = onePlusRate.pow(cycle - 1, MC);
            multiplier = factorPrev.multiply(BigDecimal.valueOf(oldDays), MC)
                    .add(factorCurr.multiply(BigDecimal.valueOf(newDays), MC), MC)
                    .divide(BigDecimal.valueOf(daysInMonth), MC);
            LocalDate escDate = LocalDate.of(ym.getYear(), ym.getMonth(), startDay);
            escalationNote = String.format("合同%s起递增：本月1-%d日按原单价、%d-%d日按新单价，按天数加权",
                    escDate, oldDays, startDay, daysInMonth);
        }

        BigDecimal area = nz(c.getLeaseArea());
        BigDecimal currRentPrice = nz(c.getRentUnitPrice()).multiply(multiplier, MC);
        BigDecimal currPropPrice = nz(c.getPropertyUnitPrice()).multiply(multiplier, MC);

        BigDecimal rawStandardRent = area.multiply(currRentPrice, MC);
        BigDecimal rawStandardProp = area.multiply(currPropPrice, MC);

        DefaultRuleResult rule = evaluateDefaultRule(ym, c, startYm, endYm, escalationNote);

        BigDecimal rentFactor = entry != null && entry.getRentFactor() != null ? entry.getRentFactor() : rule.rentFactor();
        BigDecimal propFactor = entry != null && entry.getPropertyFactor() != null ? entry.getPropertyFactor() : rule.propertyFactor();
        String remark = entry != null && entry.getRemark() != null ? entry.getRemark() : rule.remark();

        BigDecimal payableRent = entry != null && entry.getRentAmountOverride() != null
                ? money(entry.getRentAmountOverride())
                : money(rawStandardRent.multiply(rentFactor, MC));
        BigDecimal payableProp = entry != null && entry.getPropertyAmountOverride() != null
                ? money(entry.getPropertyAmountOverride())
                : money(rawStandardProp.multiply(propFactor, MC));

        ContractMonthRow row = new ContractMonthRow();
        row.setContractId(c.getId());
        row.setSeqNo(c.getSeqNo());
        row.setTenantRefId(c.getTenantRefId());
        row.setTenantName(c.getTenantName());
        row.setUnitName(c.getUnitName());
        row.setAgreementNo(c.getAgreementNo());
        row.setCollectionTiming(c.getCollectionTiming());
        row.setLeaseArea(area.setScale(2, RoundingMode.HALF_UP));
        row.setRentUnitPrice(currRentPrice.setScale(4, RoundingMode.HALF_UP));
        row.setPropertyUnitPrice(currPropPrice.setScale(4, RoundingMode.HALF_UP));
        row.setStandardRent(money(rawStandardRent));
        row.setStandardProperty(money(rawStandardProp));
        row.setDefaultRentFactor(rule.rentFactor().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros());
        row.setDefaultPropertyFactor(rule.propertyFactor().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros());
        row.setRentFactor(rentFactor.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros());
        row.setPropertyFactor(propFactor.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros());
        row.setRentAmountOverride(entry == null ? null : entry.getRentAmountOverride());
        row.setPropertyAmountOverride(entry == null ? null : entry.getPropertyAmountOverride());
        row.setPayableRent(payableRent);
        row.setPayableProperty(payableProp);
        row.setPayableTotal(money(payableRent.add(payableProp)));
        row.setDefaultRemark(rule.remark());
        row.setRemark(remark);
        row.setAdjusted(entry != null);
        return row;
    }

    private static DefaultRuleResult evaluateDefaultRule(YearMonth ym, LeaseContract c,
                                                         YearMonth startYm, YearMonth endYm,
                                                         String escalationNote) {
        JsonNode root = parseJson(c.getRulesJson());
        DefaultRuleResult base = evaluateBaseRuleForMonth(ym, root);

        if (base == null && root != null && root.has("everyPaidMonthsHalfRent") && root.get("everyPaidMonthsHalfRent").isObject()) {
            JsonNode cfg = root.get("everyPaidMonthsHalfRent");
            int paidTarget = cfg.path("paidCount").asInt(12);
            if (paidTarget > 0) {
                int fullPaidCount = 0;
                YearMonth cursor = startYm;
                while (!cursor.isAfter(ym)) {
                    DefaultRuleResult fixed = evaluateBaseRuleForMonth(cursor, root);
                    boolean isTargetMonth = cursor.equals(ym);
                    if (fixed != null) {
                        if (fixed.rentFactor().compareTo(BigDecimal.ONE) == 0) {
                            fullPaidCount++;
                        }
                        if (isTargetMonth) {
                            base = fixed;
                        }
                    } else if (fullPaidCount >= paidTarget) {
                        fullPaidCount = 0;
                        if (isTargetMonth) {
                            base = new DefaultRuleResult(
                                    decimal(cfg.get("rentFactor"), new BigDecimal("0.5")),
                                    decimal(cfg.get("propertyFactor"), BigDecimal.ONE),
                                    cfg.path("remark").asText("每交租满" + paidTarget + "个月，本月租金按5折收取")
                            );
                        }
                    } else {
                        fullPaidCount++;
                    }
                    cursor = cursor.plusMonths(1);
                }
            }
        }

        BigDecimal rentFactor = base != null ? base.rentFactor() : BigDecimal.ONE;
        BigDecimal propFactor = base != null ? base.propertyFactor() : BigDecimal.ONE;
        String remark = base != null ? base.remark() : "";

        // 末月不足整月按自然月天数折算
        if (ym.equals(endYm) && c.getEndDate().getDayOfMonth() < ym.lengthOfMonth()) {
            int days = c.getEndDate().getDayOfMonth();
            int daysInMonth = ym.lengthOfMonth();
            BigDecimal proration = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP);
            rentFactor = rentFactor.multiply(proration, MC);
            propFactor = propFactor.multiply(proration, MC);
            String endNote = String.format("合同%s到期，本月仅计%d天（按%d/%d折算）", c.getEndDate(), days, days, daysInMonth);
            remark = joinRemarks(remark, endNote);
        }

        if (StringUtils.hasText(escalationNote)) {
            remark = joinRemarks(remark, escalationNote);
        }
        return new DefaultRuleResult(rentFactor, propFactor, remark == null ? "" : remark);
    }

    private static DefaultRuleResult evaluateBaseRuleForMonth(YearMonth ym, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        String periodStr = ym.toString();
        JsonNode periods = root.get("periods");
        if (periods != null && periods.isArray()) {
            for (JsonNode p : periods) {
                String from = p.path("from").asText("");
                String to = p.path("to").asText("");
                if (!from.isEmpty() && !to.isEmpty()
                        && periodStr.compareTo(from) >= 0 && periodStr.compareTo(to) <= 0) {
                    return new DefaultRuleResult(
                            decimal(p.get("rentFactor"), BigDecimal.ONE),
                            decimal(p.get("propertyFactor"), BigDecimal.ONE),
                            p.path("remark").asText("")
                    );
                }
            }
        }
        JsonNode annual = root.get("annualMonthFree");
        if (annual != null && annual.isObject()) {
            int fromYear = annual.path("fromYear").asInt(0);
            int toYear = annual.path("toYear").asInt(9999);
            int month = annual.path("month").asInt(12);
            if (ym.getYear() >= fromYear && ym.getYear() <= toYear && ym.getMonthValue() == month) {
                return new DefaultRuleResult(
                        decimal(annual.get("rentFactor"), BigDecimal.ZERO),
                        decimal(annual.get("propertyFactor"), BigDecimal.ONE),
                        annual.path("remark").asText("")
                );
            }
        }
        return null;
    }

    /**
     * 按租户分别生成「租金缴费通知单」和「物业管理费缴费通知单」(同一租户名下多份合同合并在一张单内分行列示)。
     */
    public static List<LeaseFeeNotice> buildNotices(LeaseFeePeriod period, List<ContractMonthRow> rows) {
        Map<String, List<ContractMonthRow>> byTenant = new LinkedHashMap<>();
        for (ContractMonthRow r : rows) {
            String key = r.getTenantName() == null ? "" : r.getTenantName().trim();
            byTenant.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<LeaseFeeNotice> result = new ArrayList<>();
        String prefix = StringUtils.hasText(period.getNoticePrefix()) ? period.getNoticePrefix().trim() : "DIPARK";
        String ymCompact = period.getPeriod().replace("-", "");

        int rentIdx = 0;
        for (Map.Entry<String, List<ContractMonthRow>> e : byTenant.entrySet()) {
            rentIdx++;
            result.add(buildSingleNotice(period, LeaseFeeNotice.CAT_RENT, e.getKey(), e.getValue(),
                    String.format("%s-ZJ-%s-%02d", prefix, ymCompact, rentIdx), rentIdx));
        }
        int propIdx = 0;
        for (Map.Entry<String, List<ContractMonthRow>> e : byTenant.entrySet()) {
            propIdx++;
            result.add(buildSingleNotice(period, LeaseFeeNotice.CAT_PROPERTY, e.getKey(), e.getValue(),
                    String.format("%s-WY-%s-%02d", prefix, ymCompact, propIdx), 100 + propIdx));
        }
        return result;
    }

    private static LeaseFeeNotice buildSingleNotice(LeaseFeePeriod period, String category,
                                                    String tenantName, List<ContractMonthRow> rows,
                                                    String noticeNo, int sort) {
        boolean isRent = LeaseFeeNotice.CAT_RENT.equals(category);
        LeaseFeeNotice n = new LeaseFeeNotice();
        n.setLeasePeriodId(period.getId());
        n.setPeriod(period.getPeriod());
        n.setFeeCategory(category);
        n.setNoticeNo(noticeNo);
        n.setPayeeName(isRent ? period.getRentPayeeName() : period.getPropertyPayeeName());
        n.setPaymentAccount(isRent ? period.getRentPaymentAccount() : period.getPropertyPaymentAccount());
        n.setTenantName(tenantName);
        n.setSort(sort);
        n.setPaymentStatus(0);

        List<String> units = new ArrayList<>();
        List<String> agreements = new ArrayList<>();
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal stdSum = BigDecimal.ZERO;
        BigDecimal paySum = BigDecimal.ZERO;
        Long tenantRefId = null;

        List<LeaseFeeNoticeItem> items = new ArrayList<>();
        int idx = 0;
        for (ContractMonthRow r : rows) {
            idx++;
            if (tenantRefId == null && r.getTenantRefId() != null) {
                tenantRefId = r.getTenantRefId();
            }
            if (StringUtils.hasText(r.getUnitName()) && !units.contains(r.getUnitName())) {
                units.add(r.getUnitName());
            }
            String ag = extractCategoryAgreement(r.getAgreementNo(), isRent);
            if (StringUtils.hasText(ag) && !agreements.contains(ag)) {
                agreements.add(ag);
            }
            totalArea = totalArea.add(nz(r.getLeaseArea()));
            BigDecimal std = isRent ? nz(r.getStandardRent()) : nz(r.getStandardProperty());
            BigDecimal pay = isRent ? nz(r.getPayableRent()) : nz(r.getPayableProperty());
            stdSum = stdSum.add(std);
            paySum = paySum.add(pay);

            LeaseFeeNoticeItem it = new LeaseFeeNoticeItem();
            it.setLeasePeriodId(period.getId());
            it.setContractId(r.getContractId());
            it.setSeq(String.valueOf(idx));
            it.setItemType(isRent ? LeaseFeeNoticeItem.T_RENT : LeaseFeeNoticeItem.T_PROPERTY);
            it.setManualFlag(0);
            it.setFeeItem(isRent ? "租金" : "物业管理费");
            it.setUnitName(r.getUnitName());
            it.setLeaseArea(r.getLeaseArea());
            it.setUnitPrice(isRent ? r.getRentUnitPrice() : r.getPropertyUnitPrice());
            it.setStandardAmount(std);
            it.setDiscountFactor(isRent ? r.getRentFactor() : r.getPropertyFactor());
            it.setAmount(pay);
            it.setRemark(r.getRemark());
            it.setSort(idx * 10);
            items.add(it);
        }

        n.setTenantRefId(tenantRefId);
        n.setUnitName(String.join("、", units));
        n.setAgreementNo(agreements.isEmpty() ? null : String.join("；", agreements));
        n.setLeaseArea(totalArea.setScale(2, RoundingMode.HALF_UP));
        n.setStandardAmount(money(stdSum));
        n.setPayableAmount(money(paySum));
        n.setOtherFee(BigDecimal.ZERO);
        n.setSubtotal(money(paySum));
        n.setPaidAmount(BigDecimal.ZERO);
        n.setPriorArrears(BigDecimal.ZERO);
        n.setTotalDue(money(paySum));
        n.setItems(items);
        return n;
    }

    /** 协议编号形如 "GZCJ-xxx / WYFW-xxx", 按租金/物业分别取对应段 */
    static String extractCategoryAgreement(String raw, boolean isRent) {
        if (!StringUtils.hasText(raw)) return null;
        String trimmed = raw.trim();
        if (trimmed.contains(" / ")) {
            String[] parts = trimmed.split("\\s*/\\s*", 2);
            return isRent ? parts[0].trim() : parts[1].trim();
        }
        return trimmed;
    }

    public static void recalcNoticeTotals(LeaseFeeNotice n, List<LeaseFeeNoticeItem> items) {
        BigDecimal autoSum = BigDecimal.ZERO;
        BigDecimal otherSum = BigDecimal.ZERO;
        for (LeaseFeeNoticeItem it : items) {
            BigDecimal amt = nz(it.getAmount());
            if (it.isManualItem()) {
                otherSum = otherSum.add(amt);
            } else {
                autoSum = autoSum.add(amt);
            }
        }
        n.setPayableAmount(money(autoSum));
        n.setOtherFee(money(otherSum));
        n.setSubtotal(money(autoSum.add(otherSum)));
        n.setTotalDue(money(n.getSubtotal().subtract(nz(n.getPaidAmount())).add(nz(n.getPriorArrears()))));
    }

    public static String noticeKey(LeaseFeeNotice n) {
        return (n.getFeeCategory() == null ? "" : n.getFeeCategory()) + "|"
                + (n.getTenantName() == null ? "" : n.getTenantName().trim());
    }

    public static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) return fallback;
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String joinRemarks(String a, String b) {
        if (!StringUtils.hasText(a)) return b == null ? "" : b;
        if (!StringUtils.hasText(b)) return a;
        return a + "；" + b;
    }
}
