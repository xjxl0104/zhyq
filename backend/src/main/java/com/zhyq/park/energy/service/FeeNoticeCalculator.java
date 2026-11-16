package com.zhyq.park.energy.service;

import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 水电垃圾分摊与通知单生成的纯计算部分,不碰数据库,便于用线下表的数字做回归。
 *
 * <p>口径照 2026-11 负责人线下表《应收收费明细表》:</p>
 * <pre>
 * 电: 公摊度数 = 南网总度数 + 光伏用电 − 全部分表用量(租户表 + 物业表,含不参与公摊的充电桩/未租)
 *     某户公摊度数 = 公摊度数 × 该户用量 / 参与公摊租户用量合计
 *     电费 = 自身用量 × 单价;公摊电费 = 公摊度数 × 单价
 * 水: 公摊度数 = 总表度数 − 分表用量合计,其余同电
 * 垃圾: 生活 / 工业两笔总额 × 该户计租面积 / 参与分摊的计租面积合计
 * </pre>
 * 室外充电桩、未租单元 share_public = 0:只付自身用量,不摊公摊、不摊垃圾。
 */
public final class FeeNoticeCalculator {

    public static final String TYPE_ELECTRIC = "电";
    public static final String TYPE_WATER = "水";
    static final int COEF_SCALE = 8;
    static final int USAGE_SCALE = 4;
    static final int MONEY_SCALE = 2;

    private static final Pattern FLOOR = Pattern.compile("^\\s*(\\d+|[一二三四五六七八九十]+)\\s*[层楼Ff]");
    private static final Pattern ZONE = Pattern.compile("[层楼Ff]\\s*([A-Za-z])\\s*区?\\s*$");
    private static final String CN_DIGITS = "一二三四五六七八九十";

    private FeeNoticeCalculator() {}

    /** 一块表在某账期的一行:读数来自 eng_reading,计算列由本类填 */
    @Data
    public static class SheetRow {
        private Long meterId;
        private String code;
        private String name;
        private String area;
        private String customerName;
        private Long tenantRefId;
        private String tenantName;
        private String energyType;
        private String meterRole;
        private boolean sharePublic = true;
        private BigDecimal leaseArea;
        private BigDecimal ratio = BigDecimal.ONE;
        private Long readingId;
        private BigDecimal prevReading;
        private BigDecimal currReading;
        /** 本期用量 = (本期 − 上期) × 倍率,抄表时已算好 */
        private BigDecimal usage;
        // ---- 计算列
        private BigDecimal coefficient = BigDecimal.ZERO;
        private BigDecimal publicUsage = BigDecimal.ZERO;
        private BigDecimal totalUsage = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;
        private BigDecimal publicFee = BigDecimal.ZERO;
        private BigDecimal totalFee = BigDecimal.ZERO;

        public boolean isTenant() {
            return "TENANT".equals(meterRole);
        }

        public boolean isSharing() {
            return isTenant() && sharePublic;
        }
    }

    /** 一种能源一个账期的分摊结果 */
    public record UtilityResult(String energyType, BigDecimal total, BigDecimal submeterUsage,
                                BigDecimal publicUsage, BigDecimal shareBase, BigDecimal price,
                                BigDecimal totalFee, List<SheetRow> rows, List<String> warnings) {
        public static UtilityResult empty(String energyType, BigDecimal price) {
            return new UtilityResult(energyType, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, nz(price), BigDecimal.ZERO, List.of(), List.of());
        }
    }

    /** 通知单生成结果 */
    public record NoticeBatch(List<FeeNotice> notices, BigDecimal totalLeaseArea, List<String> warnings) {}

    /**
     * 按用量占比分摊公摊度数。rows 应只含有读数的分表(TENANT / PROPERTY);
     * total 为空或 ≤0 时不摊(公摊为 0)并给出警告。
     */
    public static UtilityResult allocate(String energyType, BigDecimal total, BigDecimal price, List<SheetRow> rows) {
        return allocate(energyType, total, price, rows, null);
    }

    /**
     * 公摊池 = 总度数 − 参与公摊租户用量 − 物业公司表用量(与线下对账表一致:
     * 室外充电桩、未租单元不参与公摊,也不从公摊池里扣,它们自己的用量另收)。
     * 各参与公摊租户按各自实际用量占比分摊公摊池。
     * publicOverride 不为空时直接用它当公摊度数(线下表里公摊度数是手填的,留空才自动算)。
     * 返回里的 submeterUsage 就是上面扣掉的那部分用量。
     */
    public static UtilityResult allocate(String energyType, BigDecimal total, BigDecimal price, List<SheetRow> rows,
                                         BigDecimal publicOverride) {
        List<String> warnings = new ArrayList<>();
        BigDecimal unitPrice = nz(price);
        BigDecimal submeter = BigDecimal.ZERO;
        BigDecimal shareBase = BigDecimal.ZERO;
        for (SheetRow r : rows) {
            BigDecimal usage = nz(r.getUsage());
            if (r.isSharing() || "PROPERTY".equals(r.getMeterRole())) {
                submeter = submeter.add(usage);
            }
            if (r.isSharing()) {
                shareBase = shareBase.add(usage);
            }
        }
        BigDecimal pool = BigDecimal.ZERO;
        if (publicOverride != null) {
            pool = publicOverride.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else if (total == null || total.signum() <= 0) {
            warnings.add(energyType + ":没有总度数,本期公摊按 0 计");
        } else {
            pool = total.subtract(submeter).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (pool.signum() < 0) {
                warnings.add(energyType + ":分表用量合计 " + plain(submeter) + " 超过总度数 " + plain(total)
                        + ",公摊按 0 计,请核对读数");
                pool = BigDecimal.ZERO;
            }
        }
        if (pool.signum() > 0 && shareBase.signum() <= 0) {
            warnings.add(energyType + ":没有参与公摊的租户用量,公摊度数 " + plain(pool) + " 无法分摊");
        }
        // 所有数都保留 2 位小数;公摊度数用最大余数法摊,保证各户公摊之和恰好等于公摊池,页面与通知单才对得上
        List<BigDecimal> shares = apportion(pool, rows.stream().filter(SheetRow::isSharing)
                .map(r -> nz(r.getUsage())).toList());
        int shareIdx = 0;
        BigDecimal totalFee = BigDecimal.ZERO;
        for (SheetRow r : rows) {
            BigDecimal usage = nz(r.getUsage());
            BigDecimal coef = BigDecimal.ZERO;
            BigDecimal pub = BigDecimal.ZERO;
            if (r.isSharing()) {
                pub = shares.get(shareIdx++);
                if (shareBase.signum() > 0) {
                    coef = usage.divide(shareBase, COEF_SCALE, RoundingMode.HALF_UP);
                }
            }
            r.setCoefficient(coef);
            r.setPublicUsage(pub);
            r.setTotalUsage(usage.add(pub));
            r.setFee(money(usage.multiply(unitPrice)));
            r.setPublicFee(money(pub.multiply(unitPrice)));
            r.setTotalFee(r.getFee().add(r.getPublicFee()));
            if (r.isTenant()) {
                totalFee = totalFee.add(r.getTotalFee());
            }
        }
        return new UtilityResult(energyType, nz(total), submeter, pool, shareBase, unitPrice, totalFee, rows, warnings);
    }

    /** 把两种能源的分摊结果按「租户 × 楼层」合成通知单,并摊垃圾费 */
    public static NoticeBatch buildNotices(FeePeriod period, UtilityResult water, UtilityResult electric,
                                           Map<String, FeeNotice> previous) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(water.warnings());
        warnings.addAll(electric.warnings());

        // 1. 按户分组:一户 = 同一租户在同一楼层的所有单元
        Map<String, List<SheetRow>> groups = new LinkedHashMap<>();
        List<SheetRow> tenantRows = new ArrayList<>();
        water.rows().stream().filter(SheetRow::isTenant).forEach(tenantRows::add);
        electric.rows().stream().filter(SheetRow::isTenant).forEach(tenantRows::add);
        tenantRows.sort(Comparator.comparing((SheetRow r) -> floorOrder(floorKey(r.getArea())))
                .thenComparing(r -> unitKey(r), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.getMeterId() == null ? 0L : r.getMeterId()));
        for (SheetRow r : tenantRows) {
            groups.computeIfAbsent(tenantKey(r) + "|" + floorKey(r.getArea()), k -> new ArrayList<>()).add(r);
        }

        // 2. 垃圾费按单元计租面积分摊(最大余数法,各单元之和恰好等于总额),只有参与分摊的单元进分母
        GarbageSplit split = splitGarbage(period, tenantRows);
        BigDecimal totalArea = split.totalArea();

        // 3. 逐户生成通知单与明细行
        List<FeeNotice> notices = new ArrayList<>();
        Map<String, Integer> seqByFloor = new LinkedHashMap<>();
        int sort = 0;
        for (Map.Entry<String, List<SheetRow>> g : groups.entrySet()) {
            List<SheetRow> rows = g.getValue();
            SheetRow first = rows.get(0);
            String floorKey = floorKey(first.getArea());
            Set<String> units = new LinkedHashSet<>();
            rows.forEach(r -> units.add(unitKey(r)));
            boolean share = rows.stream().anyMatch(SheetRow::isSharePublic);
            BigDecimal area = BigDecimal.ZERO;
            BigDecimal domestic = BigDecimal.ZERO;
            BigDecimal industrial = BigDecimal.ZERO;
            for (String u : units) {
                UnitShare us = split.units().get(u);
                if (us == null) {
                    continue;
                }
                area = area.add(us.area());
                domestic = domestic.add(us.domestic());
                industrial = industrial.add(us.industrial());
            }
            if (share && area.signum() <= 0) {
                warnings.add("「" + unitName(floorKey, units) + " " + firstNonBlank(first.getTenantName(),
                        first.getCustomerName(), "未知租户") + "」没有填计租面积,垃圾费分摊时按 0 计");
            }

            FeeNotice n = new FeeNotice();
            n.setFeePeriodId(period.getId());
            n.setPeriod(period.getPeriod());
            n.setTenantRefId(first.getTenantRefId());
            n.setTenantName(firstNonBlank(first.getTenantName(), first.getCustomerName(), "未知租户"));
            n.setUnitName(unitName(floorKey, units));
            n.setFloorKey(floorKey);
            n.setLeaseArea(area);
            n.setSharePublic(share ? 1 : 0);
            n.setSort(++sort);
            int seq = seqByFloor.merge(floorKey, 1, Integer::sum);
            n.setNoticeNo(noticeNo(period, floorKey, seq));

            List<FeeNoticeItem> items = new ArrayList<>();
            List<SheetRow> waterRows = rows.stream().filter(r -> TYPE_WATER.equals(r.getEnergyType())).toList();
            List<SheetRow> electricRows = rows.stream().filter(r -> TYPE_ELECTRIC.equals(r.getEnergyType())).toList();
            int[] no = {0};
            if (!waterRows.isEmpty()) {
                addUtilityItems(items, no, waterRows, water.price(), "m³", "水费", "公摊水费",
                        FeeNoticeItem.T_WATER, FeeNoticeItem.T_WATER_DETAIL, FeeNoticeItem.T_WATER_PUBLIC, "水表");
                n.setWaterUsage(sum(waterRows, SheetRow::getUsage));
                n.setWaterPublicUsage(sum(waterRows, SheetRow::getPublicUsage));
                n.setWaterFee(sum(waterRows, SheetRow::getFee));
                n.setWaterPublicFee(sum(waterRows, SheetRow::getPublicFee));
            } else {
                zeroWater(n);
            }
            if (!electricRows.isEmpty()) {
                addUtilityItems(items, no, electricRows, electric.price(), "度", "电费", "公摊电费",
                        FeeNoticeItem.T_ELECTRIC, FeeNoticeItem.T_ELECTRIC_DETAIL, FeeNoticeItem.T_ELECTRIC_PUBLIC, "电表");
                n.setElectricUsage(sum(electricRows, SheetRow::getUsage));
                n.setElectricPublicUsage(sum(electricRows, SheetRow::getPublicUsage));
                n.setElectricFee(sum(electricRows, SheetRow::getFee));
                n.setElectricPublicFee(sum(electricRows, SheetRow::getPublicFee));
            } else {
                zeroElectric(n);
            }

            if (share) {
                items.add(garbageItem(++no[0], FeeNoticeItem.T_GARBAGE_DOMESTIC, "生活垃圾清运费", domestic,
                        "生活垃圾", period.getDomesticGarbageNote(), period.getDomesticGarbageAmount(), area, totalArea));
                items.add(garbageItem(++no[0], FeeNoticeItem.T_GARBAGE_INDUSTRIAL, "工业垃圾清运费", industrial,
                        "工业垃圾", period.getIndustrialGarbageNote(), period.getIndustrialGarbageAmount(), area, totalArea));
            }
            n.setDomesticGarbageFee(domestic);
            n.setIndustrialGarbageFee(industrial);

            // 手工填的 已付 / 前期欠费 / 其他费用 / 备注 在重新生成时保留
            FeeNotice prev = previous == null ? null : previous.get(noticeKey(n));
            n.setPaidAmount(prev == null ? BigDecimal.ZERO : nz(prev.getPaidAmount()));
            n.setPriorArrears(prev == null ? BigDecimal.ZERO : nz(prev.getPriorArrears()));
            n.setOtherFee(prev == null ? BigDecimal.ZERO : nz(prev.getOtherFee()));
            n.setOtherFeeNote(prev == null ? null : prev.getOtherFeeNote());
            n.setRemark(prev == null ? null : prev.getRemark());
            if (n.getOtherFee().signum() != 0) {
                items.add(otherItem(++no[0], n.getOtherFee(), n.getOtherFeeNote()));
            }
            int itemSort = 0;
            for (FeeNoticeItem it : items) {
                it.setSort(++itemSort);
            }
            n.setItems(items);
            recalcTotals(n);
            if (n.getTenantRefId() == null) {
                warnings.add("「" + n.getUnitName() + " " + n.getTenantName() + "」没有关联到在租合同,通知单可打印但确认出账时会跳过");
            }
            notices.add(n);
        }
        return new NoticeBatch(notices, totalArea, warnings);
    }

    /** 小计与应收合计:改了 已付/前期欠费/其他费用 后要重算 */
    public static void recalcTotals(FeeNotice n) {
        BigDecimal subtotal = nz(n.getWaterFee()).add(nz(n.getWaterPublicFee()))
                .add(nz(n.getElectricFee())).add(nz(n.getElectricPublicFee()))
                .add(nz(n.getDomesticGarbageFee())).add(nz(n.getIndustrialGarbageFee()))
                .add(nz(n.getOtherFee()));
        n.setSubtotal(money(subtotal));
        n.setTotalDue(money(subtotal.subtract(nz(n.getPaidAmount())).add(nz(n.getPriorArrears()))));
    }

    /** 重新生成时用来找回上一版手工字段的键 */
    public static String noticeKey(FeeNotice n) {
        return (n.getTenantRefId() != null ? "T" + n.getTenantRefId() : "N" + n.getTenantName())
                + "|" + n.getFloorKey() + "|" + n.getUnitName();
    }

    // ---------------------------------------------------------------- 取整与垃圾分摊

    /**
     * 把 total 按权重分成若干份,每份保留 2 位小数,各份之和恰好等于 total(最大余数法)。
     * 权重全为 0 或 total ≤ 0 时每份都是 0。
     */
    public static List<BigDecimal> apportion(BigDecimal total, List<BigDecimal> weights) {
        int n = weights.size();
        List<BigDecimal> out = new ArrayList<>(n);
        BigDecimal weightSum = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            weightSum = weightSum.add(nz(w));
        }
        long totalCents = nz(total).setScale(MONEY_SCALE, RoundingMode.HALF_UP).movePointRight(2).longValue();
        if (n == 0 || weightSum.signum() <= 0 || totalCents <= 0) {
            for (int i = 0; i < n; i++) {
                out.add(money(BigDecimal.ZERO));
            }
            return out;
        }
        long[] cents = new long[n];
        BigDecimal[] remainder = new BigDecimal[n];
        long allocated = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = BigDecimal.valueOf(totalCents).multiply(nz(weights.get(i)))
                    .divide(weightSum, 12, RoundingMode.HALF_UP);
            long floor = exact.setScale(0, RoundingMode.FLOOR).longValue();
            cents[i] = floor;
            remainder[i] = exact.subtract(BigDecimal.valueOf(floor));
            allocated += floor;
        }
        long left = Math.min(totalCents - allocated, n);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> remainder[i]).reversed().thenComparing(i -> i));
        for (int k = 0; k < left; k++) {
            cents[order.get(k)]++;
        }
        for (int i = 0; i < n; i++) {
            out.add(BigDecimal.valueOf(cents[i], 2));
        }
        return out;
    }

    /** 一个单元的垃圾费分摊结果 */
    public record UnitShare(String unit, BigDecimal area, boolean share, BigDecimal ratio,
                            BigDecimal domestic, BigDecimal industrial) {}

    public record GarbageSplit(Map<String, UnitShare> units, BigDecimal totalArea) {}

    /**
     * 垃圾费分摊:生活 / 工业两笔总额按各单元计租面积占比摊,只有参与分摊(share_public=1)且填了面积的单元参与。
     * 单元 = 表计「区域」;同一区域多块表(水表、30倍/80倍电表)面积取其中最大值。
     * 通知单与垃圾处理费页面都调用这里,保证两边金额逐分一致。
     */
    public static GarbageSplit splitGarbage(FeePeriod period, List<SheetRow> tenantRows) {
        Map<String, BigDecimal> area = new LinkedHashMap<>();
        Map<String, Boolean> share = new LinkedHashMap<>();
        for (SheetRow r : tenantRows) {
            if (!r.isTenant()) {
                continue;
            }
            String unit = unitKey(r);
            share.merge(unit, r.isSharePublic(), Boolean::logicalOr);
            BigDecimal a = r.getLeaseArea() != null && r.getLeaseArea().signum() > 0 ? r.getLeaseArea() : BigDecimal.ZERO;
            area.merge(unit, a, BigDecimal::max);
        }
        // 顺序固定(楼层 → 区域名),最大余数法的并列才不会随查询顺序漂移
        List<String> units = new ArrayList<>(area.keySet());
        units.sort(Comparator.comparing((String u) -> floorOrder(floorKey(u))).thenComparing(u -> u, String.CASE_INSENSITIVE_ORDER));
        List<String> sharing = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        BigDecimal totalArea = BigDecimal.ZERO;
        for (String u : units) {
            if (Boolean.TRUE.equals(share.get(u)) && area.get(u).signum() > 0) {
                sharing.add(u);
                weights.add(area.get(u));
                totalArea = totalArea.add(area.get(u));
            }
        }
        List<BigDecimal> domestic = apportion(period.getDomesticGarbageAmount(), weights);
        List<BigDecimal> industrial = apportion(period.getIndustrialGarbageAmount(), weights);
        Map<String, UnitShare> out = new LinkedHashMap<>();
        for (String u : units) {
            int idx = sharing.indexOf(u);
            boolean sharesGarbage = idx >= 0;
            BigDecimal ratio = sharesGarbage && totalArea.signum() > 0
                    ? area.get(u).divide(totalArea, COEF_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            out.put(u, new UnitShare(u, area.get(u), Boolean.TRUE.equals(share.get(u)), ratio,
                    sharesGarbage ? domestic.get(idx) : money(BigDecimal.ZERO),
                    sharesGarbage ? industrial.get(idx) : money(BigDecimal.ZERO)));
        }
        return new GarbageSplit(out, totalArea);
    }

    // ---------------------------------------------------------------- 明细行

    private static void addUtilityItems(List<FeeNoticeItem> items, int[] no, List<SheetRow> rows, BigDecimal price,
                                        String unit, String label, String publicLabel,
                                        String type, String detailType, String publicType, String meterLabel) {
        int main = ++no[0];
        FeeNoticeItem item = new FeeNoticeItem();
        item.setSeq(String.valueOf(main));
        item.setItemType(type);
        item.setFeeItem(label);
        item.setUnit(unit);
        item.setUnitPrice(price);
        if (rows.size() == 1) {
            SheetRow r = rows.get(0);
            item.setMeterId(r.getMeterId());
            item.setMeterCode(r.getCode());
            item.setRatio(r.getRatio());
            item.setPrevReading(r.getPrevReading());
            item.setCurrReading(r.getCurrReading());
        } else {
            item.setPrevReading(sum(rows, SheetRow::getPrevReading));
            item.setCurrReading(sum(rows, SheetRow::getCurrReading));
        }
        BigDecimal qty = sum(rows, SheetRow::getUsage);
        item.setQuantity(qty);
        item.setAmount(sum(rows, SheetRow::getFee));
        item.setRemark("本期" + label + plain(qty.setScale(2, RoundingMode.HALF_UP)) + unit);
        items.add(item);
        if (rows.size() > 1) {
            int sub = 0;
            for (SheetRow r : rows) {
                FeeNoticeItem d = new FeeNoticeItem();
                d.setSeq(main + "." + (++sub));
                d.setItemType(detailType);
                d.setFeeItem(plain(nz(r.getRatio()).stripTrailingZeros()) + "倍" + meterLabel
                        + (r.getCode() == null ? "" : " " + r.getCode()));
                d.setMeterId(r.getMeterId());
                d.setMeterCode(r.getCode());
                d.setRatio(r.getRatio());
                d.setPrevReading(r.getPrevReading());
                d.setCurrReading(r.getCurrReading());
                d.setQuantity(nz(r.getUsage()));
                d.setUnit(unit);
                d.setUnitPrice(price);
                d.setRemark("本期" + d.getFeeItem() + plain(nz(r.getUsage()).setScale(2, RoundingMode.HALF_UP)) + unit);
                items.add(d);
            }
        }
        FeeNoticeItem pub = new FeeNoticeItem();
        pub.setSeq(String.valueOf(++no[0]));
        pub.setItemType(publicType);
        pub.setFeeItem(publicLabel);
        pub.setPrevReading(BigDecimal.ZERO);
        BigDecimal pubQty = sum(rows, SheetRow::getPublicUsage);
        pub.setCurrReading(pubQty);
        pub.setQuantity(pubQty);
        pub.setUnit(unit);
        pub.setUnitPrice(price);
        pub.setAmount(sum(rows, SheetRow::getPublicFee));
        pub.setRemark("本期" + publicLabel + plain(pubQty.setScale(2, RoundingMode.HALF_UP)) + unit
                + ",按实际用量占比" + percent(sum(rows, SheetRow::getCoefficient)) + "分摊");
        items.add(pub);
    }

    private static FeeNoticeItem garbageItem(int no, String type, String label, BigDecimal amount,
                                             String kind, String note, BigDecimal total,
                                             BigDecimal area, BigDecimal totalArea) {
        FeeNoticeItem item = new FeeNoticeItem();
        item.setSeq(String.valueOf(no));
        item.setItemType(type);
        item.setFeeItem(label);
        item.setUnit("元");
        item.setAmount(amount);
        StringBuilder remark = new StringBuilder(kind);
        if (note != null && !note.isBlank()) {
            remark.append(note.trim());
        }
        remark.append("总支出").append(plain(money(nz(total)))).append("元,按计租面积分摊(")
                .append(plain(nz(area).stripTrailingZeros())).append("㎡/")
                .append(plain(nz(totalArea).stripTrailingZeros())).append("㎡=")
                .append(percent(totalArea.signum() > 0 ? nz(area).divide(totalArea, COEF_SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO))
                .append(")").append(plain(amount)).append("元");
        item.setRemark(remark.toString());
        return item;
    }

    private static FeeNoticeItem otherItem(int no, BigDecimal amount, String note) {
        FeeNoticeItem item = new FeeNoticeItem();
        item.setSeq(String.valueOf(no));
        item.setItemType(FeeNoticeItem.T_OTHER);
        item.setFeeItem(note == null || note.isBlank() ? "其他费用" : note.trim());
        item.setUnit("元");
        item.setAmount(money(amount));
        return item;
    }

    private static void zeroWater(FeeNotice n) {
        n.setWaterUsage(BigDecimal.ZERO);
        n.setWaterPublicUsage(BigDecimal.ZERO);
        n.setWaterFee(BigDecimal.ZERO);
        n.setWaterPublicFee(BigDecimal.ZERO);
    }

    private static void zeroElectric(FeeNotice n) {
        n.setElectricUsage(BigDecimal.ZERO);
        n.setElectricPublicUsage(BigDecimal.ZERO);
        n.setElectricFee(BigDecimal.ZERO);
        n.setElectricPublicFee(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- 分组与命名

    static String tenantKey(SheetRow r) {
        if (r.getTenantRefId() != null) {
            return "T" + r.getTenantRefId();
        }
        return "N" + firstNonBlank(r.getTenantName(), r.getCustomerName(), "?");
    }

    static String unitKey(SheetRow r) {
        String area = r.getArea() == null ? "" : r.getArea().trim();
        return area.isEmpty() ? "表计" + r.getCode() : area;
    }

    /** 「2层A区」→ "2";「八楼B区」→ "8";「室外」→ "室外" */
    static String floorKey(String area) {
        if (area == null || area.isBlank()) {
            return "其他";
        }
        Matcher m = FLOOR.matcher(area.trim());
        if (m.find()) {
            String raw = m.group(1);
            return String.valueOf(parseFloor(raw));
        }
        return area.trim();
    }

    private static int parseFloor(String raw) {
        if (raw.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(raw);
        }
        // 一 ~ 十九 的简单中文数字
        int value = 0;
        for (char c : raw.toCharArray()) {
            int d = CN_DIGITS.indexOf(c) + 1;
            if (c == '十') {
                value = value == 0 ? 10 : value * 10;
            } else if (d > 0) {
                value += d;
            }
        }
        return value;
    }

    private static long floorOrder(String floorKey) {
        return floorKey.chars().allMatch(Character::isDigit) ? Long.parseLong(floorKey) : 1000L;
    }

    /** 「2层A区 + 2层B区 + 2层C区」→ "2层A~C区";不连续 → "2层A、C区";只有一个单元 → 原样 */
    static String unitName(String floorKey, Set<String> units) {
        if (units.size() == 1) {
            return units.iterator().next();
        }
        boolean numericFloor = floorKey.chars().allMatch(Character::isDigit);
        List<Character> zones = new ArrayList<>();
        for (String u : units) {
            Matcher m = ZONE.matcher(u);
            if (!numericFloor || !m.find()) {
                return String.join("、", units);
            }
            zones.add(Character.toUpperCase(m.group(1).charAt(0)));
        }
        zones.sort(Comparator.naturalOrder());
        boolean contiguous = true;
        for (int i = 1; i < zones.size(); i++) {
            if (zones.get(i) != zones.get(i - 1) + 1) {
                contiguous = false;
                break;
            }
        }
        StringBuilder sb = new StringBuilder(floorKey).append("层");
        if (contiguous) {
            sb.append(zones.get(0)).append("~").append(zones.get(zones.size() - 1));
        } else {
            for (int i = 0; i < zones.size(); i++) {
                sb.append(i > 0 ? "、" : "").append(zones.get(i));
            }
        }
        return sb.append("区").toString();
    }

    static String noticeNo(FeePeriod period, String floorKey, int seq) {
        String prefix = firstNonBlank(period.getNoticePrefix(), "DIPARK");
        String ym = period.getPeriod() == null ? "" : period.getPeriod().replace("-", "");
        String floor = floorKey.chars().allMatch(Character::isDigit) ? floorKey + "F" : floorKey;
        return prefix + "-" + ym + "-" + floor + "-" + String.format("%02d", seq);
    }

    // ---------------------------------------------------------------- 小工具

    private static BigDecimal sum(List<SheetRow> rows, java.util.function.Function<SheetRow, BigDecimal> f) {
        BigDecimal s = BigDecimal.ZERO;
        for (SheetRow r : rows) {
            s = s.add(nz(f.apply(r)));
        }
        return s;
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    /** 0.0561445 → "5.61%" */
    static String percent(BigDecimal ratio) {
        return nz(ratio).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String plain(BigDecimal v) {
        return nz(v).toPlainString();
    }
}
