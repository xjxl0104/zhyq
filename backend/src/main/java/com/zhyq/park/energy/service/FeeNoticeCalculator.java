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
import java.util.Objects;
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

    /** 把两种能源的分摊结果按「租户 × 区域」合成通知单(2层A区、2层B区各出一张),并摊垃圾费 */
    public static NoticeBatch buildNotices(FeePeriod period, UtilityResult water, UtilityResult electric,
                                           Map<String, FeeNotice> previous) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(water.warnings());
        warnings.addAll(electric.warnings());

        // 1. 按租户分组:一个租户一张通知单,名下所有区域(单元)的水电垃圾费都在这张单上,明细按区域分行
        Map<String, List<SheetRow>> groups = new LinkedHashMap<>();
        List<SheetRow> tenantRows = new ArrayList<>();
        water.rows().stream().filter(SheetRow::isTenant).forEach(tenantRows::add);
        electric.rows().stream().filter(SheetRow::isTenant).forEach(tenantRows::add);
        tenantRows.sort(Comparator.comparing((SheetRow r) -> floorOrder(floorKey(r.getArea())))
                .thenComparing(r -> unitKey(r), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.getMeterId() == null ? 0L : r.getMeterId()));
        for (SheetRow r : tenantRows) {
            groups.computeIfAbsent(tenantKey(r), k -> new ArrayList<>()).add(r);
        }

        // 2. 垃圾费按单元计租面积分摊(最大余数法,各单元之和恰好等于总额),只有参与分摊的单元进分母
        GarbageSplit split = splitGarbage(period, tenantRows);
        BigDecimal totalArea = split.totalArea();

        // 3. 逐租户生成通知单与明细行
        List<FeeNotice> notices = new ArrayList<>();
        int sort = 0;
        for (List<SheetRow> rows : groups.values()) {
            SheetRow first = rows.get(0);
            Map<String, List<SheetRow>> byUnit = new LinkedHashMap<>();
            rows.forEach(r -> byUnit.computeIfAbsent(unitKey(r), k -> new ArrayList<>()).add(r));
            boolean multi = byUnit.size() > 1;
            boolean share = rows.stream().anyMatch(SheetRow::isSharePublic);

            FeeNotice n = new FeeNotice();
            n.setFeePeriodId(period.getId());
            n.setPeriod(period.getPeriod());
            n.setTenantRefId(first.getTenantRefId());
            n.setTenantName(firstNonBlank(first.getTenantName(), first.getCustomerName(), "未知租户"));
            n.setUnitName(unitNames(byUnit.keySet()));
            n.setFloorKey(floorKeys(byUnit.keySet()));
            n.setSharePublic(share ? 1 : 0);
            n.setSort(++sort);
            n.setNoticeNo(noticeNo(period, sort));
            zeroWater(n);
            zeroElectric(n);

            List<FeeNoticeItem> items = new ArrayList<>();
            int[] no = {0};
            BigDecimal area = BigDecimal.ZERO;
            BigDecimal domestic = BigDecimal.ZERO;
            BigDecimal industrial = BigDecimal.ZERO;
            for (Map.Entry<String, List<SheetRow>> ue : byUnit.entrySet()) {
                String unit = ue.getKey();
                List<SheetRow> urows = ue.getValue();
                // 租户有多个区域时,每行费用项目前面带上区域名;只有一个区域就保持原样
                String pre = multi ? unit + " " : "";
                List<SheetRow> waterRows = urows.stream().filter(r -> TYPE_WATER.equals(r.getEnergyType())).toList();
                List<SheetRow> electricRows = urows.stream().filter(r -> TYPE_ELECTRIC.equals(r.getEnergyType())).toList();
                if (!waterRows.isEmpty()) {
                    addUtilityItems(items, no, waterRows, water.price(), "m³", pre + "水费", pre + "公摊水费",
                            FeeNoticeItem.T_WATER, FeeNoticeItem.T_WATER_DETAIL, FeeNoticeItem.T_WATER_PUBLIC, "水表");
                    n.setWaterUsage(n.getWaterUsage().add(sum(waterRows, SheetRow::getUsage)));
                    n.setWaterPublicUsage(n.getWaterPublicUsage().add(sum(waterRows, SheetRow::getPublicUsage)));
                    n.setWaterFee(n.getWaterFee().add(sum(waterRows, SheetRow::getFee)));
                    n.setWaterPublicFee(n.getWaterPublicFee().add(sum(waterRows, SheetRow::getPublicFee)));
                }
                if (!electricRows.isEmpty()) {
                    addUtilityItems(items, no, electricRows, electric.price(), "度", pre + "电费", pre + "公摊电费",
                            FeeNoticeItem.T_ELECTRIC, FeeNoticeItem.T_ELECTRIC_DETAIL, FeeNoticeItem.T_ELECTRIC_PUBLIC, "电表");
                    n.setElectricUsage(n.getElectricUsage().add(sum(electricRows, SheetRow::getUsage)));
                    n.setElectricPublicUsage(n.getElectricPublicUsage().add(sum(electricRows, SheetRow::getPublicUsage)));
                    n.setElectricFee(n.getElectricFee().add(sum(electricRows, SheetRow::getFee)));
                    n.setElectricPublicFee(n.getElectricPublicFee().add(sum(electricRows, SheetRow::getPublicFee)));
                }
                // 垃圾费:这个区域参与分摊才有,按它自己的计租面积
                UnitShare us = split.units().get(new UnitTenant(unit, tenantKey(first)));
                BigDecimal unitArea = us == null ? BigDecimal.ZERO : us.area();
                BigDecimal unitDomestic = us == null ? money(BigDecimal.ZERO) : us.domestic();
                BigDecimal unitIndustrial = us == null ? money(BigDecimal.ZERO) : us.industrial();
                area = area.add(unitArea);
                domestic = domestic.add(unitDomestic);
                industrial = industrial.add(unitIndustrial);
                if (urows.stream().anyMatch(SheetRow::isSharePublic)) {
                    if (unitArea.signum() <= 0) {
                        warnings.add("「" + unit + " " + n.getTenantName() + "」没有填计租面积,垃圾费分摊时按 0 计");
                    }
                    items.add(garbageItem(++no[0], FeeNoticeItem.T_GARBAGE_DOMESTIC, pre + "生活垃圾清运费", unitDomestic,
                            "生活垃圾", period.getDomesticGarbageNote(), period.getDomesticGarbageAmount(), unitArea, totalArea));
                    items.add(garbageItem(++no[0], FeeNoticeItem.T_GARBAGE_INDUSTRIAL, pre + "工业垃圾清运费", unitIndustrial,
                            "工业垃圾", period.getIndustrialGarbageNote(), period.getIndustrialGarbageAmount(), unitArea, totalArea));
                }
            }
            n.setLeaseArea(area);
            n.setDomesticGarbageFee(domestic);
            n.setIndustrialGarbageFee(industrial);

            // 手工填的 已付 / 前期欠费 / 其他费用 / 备注 在重新生成时保留
            FeeNotice prev = previous == null ? null : previous.get(noticeKey(n));
            n.setPaidAmount(prev == null ? BigDecimal.ZERO : nz(prev.getPaidAmount()));
            n.setPaymentStatus(prev != null && Integer.valueOf(1).equals(prev.getPaymentStatus()) ? 1 : 0);
            n.setInvoiceStatus(prev != null && Integer.valueOf(1).equals(prev.getInvoiceStatus()) ? 1 : 0);
            n.setPriorArrears(prev == null ? BigDecimal.ZERO : nz(prev.getPriorArrears()));
            n.setRemark(prev == null ? null : prev.getRemark());
            keepRemarkEdits(items, prev);
            // 手工新增的收费行逐行找回;老数据只有「其他费用」一个金额、没有明细行时,还原成一行
            List<FeeNoticeItem> manual = prev == null ? List.of()
                    : prev.getItems().stream().filter(FeeNoticeItem::isManualItem).toList();
            if (manual.isEmpty() && prev != null && nz(prev.getOtherFee()).signum() != 0) {
                items.add(otherItem(++no[0], prev.getOtherFee(), prev.getOtherFeeNote()));
            }
            for (FeeNoticeItem m : manual) {
                items.add(copyManual(++no[0], m));
            }
            applyOtherFee(n, items);
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
        return n.getTenantRefId() != null ? "T" + n.getTenantRefId() : "N" + n.getTenantName();
    }

    /**
     * 上一版通知单按租户归并成「找回手工内容」用的底稿:一个租户以前若出过多张(按楼层或区域分开的旧版),
     * 已付/前期欠费相加,备注取第一个有的,手工收费行与手改备注的明细行全部带上。
     */
    public static Map<String, FeeNotice> mergePrevious(List<FeeNotice> olds) {
        Map<String, FeeNotice> out = new LinkedHashMap<>();
        for (FeeNotice old : olds) {
            String key = noticeKey(old);
            FeeNotice head = out.get(key);
            if (head == null) {
                head = new FeeNotice();
                head.setTenantRefId(old.getTenantRefId());
                head.setTenantName(old.getTenantName());
                head.setPaidAmount(nz(old.getPaidAmount()));
                head.setPaymentStatus(Integer.valueOf(1).equals(old.getPaymentStatus()) ? 1 : 0);
                head.setInvoiceStatus(Integer.valueOf(1).equals(old.getInvoiceStatus()) ? 1 : 0);
                head.setPriorArrears(nz(old.getPriorArrears()));
                head.setOtherFee(nz(old.getOtherFee()));
                head.setOtherFeeNote(old.getOtherFeeNote());
                head.setRemark(old.getRemark());
                head.setItems(new ArrayList<>(old.getItems()));
                out.put(key, head);
                continue;
            }
            head.setPaidAmount(nz(head.getPaidAmount()).add(nz(old.getPaidAmount())));
            head.setPaymentStatus(Integer.valueOf(1).equals(head.getPaymentStatus())
                    && Integer.valueOf(1).equals(old.getPaymentStatus()) ? 1 : 0);
            head.setInvoiceStatus(Integer.valueOf(1).equals(head.getInvoiceStatus())
                    && Integer.valueOf(1).equals(old.getInvoiceStatus()) ? 1 : 0);
            head.setPriorArrears(nz(head.getPriorArrears()).add(nz(old.getPriorArrears())));
            head.setOtherFee(nz(head.getOtherFee()).add(nz(old.getOtherFee())));
            if (head.getRemark() == null || head.getRemark().isBlank()) {
                head.setRemark(old.getRemark());
            }
            head.getItems().addAll(old.getItems());
        }
        return out;
    }

    /** 一张通知单上的所有区域:同一层合并成 2层A~C区,层与层之间用「、」隔开 */
    static String unitNames(java.util.Collection<String> units) {
        Map<String, Set<String>> byFloor = new LinkedHashMap<>();
        for (String u : units) {
            byFloor.computeIfAbsent(floorKey(u), k -> new LinkedHashSet<>()).add(u);
        }
        List<String> parts = new ArrayList<>();
        byFloor.forEach((f, us) -> parts.add(unitName(f, us)));
        return String.join("、", parts);
    }

    /** 通知单涉及的楼层键,逗号隔开(字段只有 32 个字符,放不下就截断) */
    static String floorKeys(java.util.Collection<String> units) {
        Set<String> floors = new LinkedHashSet<>();
        units.forEach(u -> floors.add(floorKey(u)));
        String joined = String.join(",", floors);
        return joined.length() > 32 ? joined.substring(0, 32) : joined;
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

    public record UnitTenant(String unit, String tenant) {}

    public record GarbageSplit(Map<UnitTenant, UnitShare> units, BigDecimal totalArea) {}

    /**
     * 垃圾费分摊:生活 / 工业两笔总额按各单元计租面积占比摊,只有参与分摊(share_public=1)且填了面积的单元参与。
     * 单元 = 表计「区域」;同一区域多块表(水表、30倍/80倍电表)面积取其中最大值。
     * 通知单与垃圾处理费页面都调用这里,保证两边金额逐分一致。
     */
    public static GarbageSplit splitGarbage(FeePeriod period, List<SheetRow> tenantRows) {
        Map<UnitTenant, BigDecimal> area = new LinkedHashMap<>();
        Map<UnitTenant, Boolean> share = new LinkedHashMap<>();
        for (SheetRow r : tenantRows) {
            if (!r.isTenant()) {
                continue;
            }
            UnitTenant unit = new UnitTenant(unitKey(r), tenantKey(r));
            share.merge(unit, r.isSharePublic(), Boolean::logicalOr);
            BigDecimal a = r.getLeaseArea() != null && r.getLeaseArea().signum() > 0 ? r.getLeaseArea() : BigDecimal.ZERO;
            area.merge(unit, a, BigDecimal::max);
        }
        // 顺序固定(楼层 → 区域名),最大余数法的并列才不会随查询顺序漂移
        List<UnitTenant> units = new ArrayList<>(area.keySet());
        units.sort(Comparator.comparing((UnitTenant u) -> floorOrder(floorKey(u.unit())))
                .thenComparing(UnitTenant::unit).thenComparing(UnitTenant::tenant));
        List<UnitTenant> sharing = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        BigDecimal totalArea = BigDecimal.ZERO;
        for (UnitTenant u : units) {
            if (Boolean.TRUE.equals(share.get(u)) && area.get(u).signum() > 0) {
                sharing.add(u);
                weights.add(area.get(u));
                totalArea = totalArea.add(area.get(u));
            }
        }
        List<BigDecimal> domestic = apportion(period.getDomesticGarbageAmount(), weights);
        List<BigDecimal> industrial = apportion(period.getIndustrialGarbageAmount(), weights);
        Map<UnitTenant, UnitShare> out = new LinkedHashMap<>();
        for (UnitTenant u : units) {
            int idx = sharing.indexOf(u);
            boolean sharesGarbage = idx >= 0;
            BigDecimal ratio = sharesGarbage && totalArea.signum() > 0
                    ? area.get(u).divide(totalArea, COEF_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            out.put(u, new UnitShare(u.unit(), area.get(u), Boolean.TRUE.equals(share.get(u)), ratio,
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
        item.setRemark("本期" + bare(label) + plain(qty.setScale(2, RoundingMode.HALF_UP)) + unit);
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
        pub.setRemark("本期" + bare(publicLabel) + plain(pubQty.setScale(2, RoundingMode.HALF_UP)) + unit
                + ",按实际用量占比" + percent(sum(rows, SheetRow::getCoefficient)) + "分摊");
        items.add(pub);
    }

    /** 「2层A区 水费」→「水费」:备注里只写项目名,区域已经在项目名前面了 */
    private static String bare(String label) {
        return label == null ? "" : label.substring(label.lastIndexOf(' ') + 1);
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
        item.setManual(1);
        return item;
    }

    /** 重新生成时把上一版里手工新增的收费行原样搬过来(序号按新位置重排) */
    private static FeeNoticeItem copyManual(int no, FeeNoticeItem src) {
        FeeNoticeItem item = new FeeNoticeItem();
        item.setSeq(String.valueOf(no));
        item.setItemType(FeeNoticeItem.T_OTHER.equals(src.getItemType()) ? FeeNoticeItem.T_OTHER : FeeNoticeItem.T_CUSTOM);
        item.setFeeItem(src.getFeeItem());
        item.setQuantity(src.getQuantity());
        item.setUnit(src.getUnit());
        item.setUnitPrice(src.getUnitPrice());
        item.setAmount(src.getAmount());
        item.setRemark(src.getRemark());
        item.setManual(1);
        return item;
    }

    /** 手工改过备注的自动行(水费/电费/垃圾费等),重新生成后沿用手工备注;分表子行按表计对应 */
    private static void keepRemarkEdits(List<FeeNoticeItem> items, FeeNotice prev) {
        if (prev == null) {
            return;
        }
        for (FeeNoticeItem old : prev.getItems()) {
            if (old.isManualItem() || old.getRemarkEdited() == null || old.getRemarkEdited() != 1) {
                continue;
            }
            for (FeeNoticeItem cur : items) {
                boolean sameType = old.getItemType() != null && old.getItemType().equals(cur.getItemType());
                // 分表子行按表计对应;主行按费用项目名对应(多区域租户的项目名带区域前缀,不会串行)
                boolean same = old.isDetail() ? Objects.equals(old.getMeterId(), cur.getMeterId())
                        : Objects.equals(old.getFeeItem(), cur.getFeeItem());
                if (sameType && same) {
                    cur.setRemark(old.getRemark());
                    cur.setRemarkEdited(1);
                    break;
                }
            }
        }
    }

    /** 其他费用 = 手工收费行金额之和;名目 = 各行名称。通知单的小计、账单金额都从这里来 */
    public static void applyOtherFee(FeeNotice n, List<FeeNoticeItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        List<String> names = new ArrayList<>();
        for (FeeNoticeItem it : items) {
            if (it.isManualItem()) {
                sum = sum.add(nz(it.getAmount()));
                if (it.getFeeItem() != null && !it.getFeeItem().isBlank()) {
                    names.add(it.getFeeItem().trim());
                }
            }
        }
        n.setOtherFee(money(sum));
        n.setOtherFeeNote(names.isEmpty() ? null : String.join("、", names));
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

    /** 单据编号:前缀-年月-租户序号,如 DIPARK-202610-03 */
    static String noticeNo(FeePeriod period, int seq) {
        String prefix = firstNonBlank(period.getNoticePrefix(), "DIPARK");
        String ym = period.getPeriod() == null ? "" : period.getPeriod().replace("-", "");
        return prefix + "-" + ym + "-" + String.format("%02d", seq);
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
