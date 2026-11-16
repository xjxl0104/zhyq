package com.zhyq.park.energy.service;

import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.service.FeeNoticeCalculator.NoticeBatch;
import com.zhyq.park.energy.service.FeeNoticeCalculator.SheetRow;
import com.zhyq.park.energy.service.FeeNoticeCalculator.UtilityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用 2026-10 线下《租户对账版 应收收费明细表》的数字钉死分摊口径。
 */
class FeeNoticeCalculatorTest {

    private static SheetRow row(long id, String area, String customer, Long tenantId, String type,
                                String usage, boolean share, String area2) {
        SheetRow r = new SheetRow();
        r.setMeterId(id);
        r.setCode("M" + id);
        r.setArea(area);
        r.setCustomerName(customer);
        r.setTenantRefId(tenantId);
        r.setTenantName(tenantId == null ? null : customer + "有限公司");
        r.setEnergyType(type);
        r.setMeterRole("TENANT");
        r.setSharePublic(share);
        r.setUsage(new BigDecimal(usage));
        r.setPrevReading(BigDecimal.ZERO);
        r.setCurrReading(new BigDecimal(usage));
        r.setLeaseArea(area2 == null ? null : new BigDecimal(area2));
        return r;
    }

    /** 10 月水表汇总:总表 1126,租户合计 675,公摊 451 */
    private static List<SheetRow> octoberWater() {
        String[][] data = {
                {"1层A区", "云帕", "19", "3300"}, {"1层B区", "云山快仓", "32", "3600"}, {"1层C区", "中印物流", "113", "3500"},
                {"2层A区", "云山快仓", "140", "13500"}, {"2层B区", "云山快仓", "15", null}, {"2层C区", "云山快仓", "18", null},
                {"3层A区", "云山快仓", "59", "13500"}, {"3层B区", "云山快仓", "27", null}, {"3层C区", "云山快仓", "8", null},
                {"4层A区", "微新云仓", "13", "13500"}, {"4层B区", "微新云仓", "11", null}, {"4层C区", "微新云仓", "8", null},
                {"5层A区", "昌泰云仓", "9", "4700"}, {"5层B区", "鑫晨云仓", "6", "9000"}, {"5层C区", "鑫晨云仓", "6", null},
                {"6层A区", "万能云仓", "5", "4700"}, {"6层B区", "云山快仓", "4", "8800"}, {"6层C区", "云山快仓", "102", null},
                {"7层A区", "云山快仓", "49", "13500"}, {"7层B区", "云山快仓", "17", null}, {"7层C区", "云山快仓", "14", null}};
        List<SheetRow> rows = new ArrayList<>();
        long id = 100;
        for (String[] d : data) {
            rows.add(row(id++, d[0], d[1], (long) d[1].hashCode(), "水", d[2], true, d[3]));
        }
        return rows;
    }

    @Test
    @DisplayName("水:公摊度数 = 总表 − 分表合计,按用量占比摊回,金额与线下表一致")
    void waterAllocationMatchesSheet() {
        UtilityResult r = FeeNoticeCalculator.allocate("水", new BigDecimal("1126"), new BigDecimal("5.51"), octoberWater());

        assertThat(r.submeterUsage()).isEqualByComparingTo("675");
        assertThat(r.publicUsage()).isEqualByComparingTo("451");
        SheetRow yunpa = r.rows().get(0);
        // 全部保留 2 位小数,12.6948 取 12.69;公摊费 = 12.69 × 5.51
        assertThat(yunpa.getPublicUsage()).isEqualByComparingTo("12.69");
        assertThat(yunpa.getFee()).isEqualByComparingTo("104.69");
        assertThat(yunpa.getPublicFee()).isEqualByComparingTo("69.92");
        // 各户公摊度数之和恰好等于公摊池 451,一分都不差
        BigDecimal allocated = r.rows().stream().map(SheetRow::getPublicUsage).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo("451");
        // 页面合计 = 各行之和
        BigDecimal feeSum = r.rows().stream().map(SheetRow::getTotalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(r.totalFee()).isEqualByComparingTo(feeSum);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("电:南网+光伏 − 全部分表 = 公摊;充电桩与未租单元不参与公摊、只付自身用量")
    void electricExcludesNonSharingMeters() {
        List<SheetRow> rows = List.of(
                row(1, "1层A区", "云帕", 1L, "电", "8003.4", true, null),
                row(2, "1层A区", "云帕", 1L, "电", "19200", true, null),
                row(3, "2层A区", "云山快仓", 2L, "电", "14481.6", true, null),
                row(4, "室外", "充电柱", null, "电", "364", false, null),
                row(5, "8层A区", "未租", null, "电", "54.4", false, null));
        // 总度数 = 南网 + 光伏;公摊 = 42103.4 − 41703.4 ... 这里构造一个整数好核的场景
        BigDecimal total = new BigDecimal("50103.4");
        UtilityResult r = FeeNoticeCalculator.allocate("电", total, new BigDecimal("1.03"), rows);

        // 公摊池 = 总度数 − 参与公摊租户用量;充电桩、未租单元既不参与也不扣
        assertThat(r.submeterUsage()).isEqualByComparingTo("41685");
        assertThat(r.publicUsage()).isEqualByComparingTo("8418.40");
        assertThat(r.shareBase()).isEqualByComparingTo("41685");
        SheetRow pile = r.rows().get(3);
        assertThat(pile.getPublicUsage()).isEqualByComparingTo("0");
        assertThat(pile.getFee()).isEqualByComparingTo("374.92");
        assertThat(pile.getTotalFee()).isEqualByComparingTo("374.92");
        BigDecimal shared = r.rows().stream().filter(SheetRow::isSharing)
                .map(SheetRow::getPublicUsage).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shared).isEqualByComparingTo("8418.40");
    }

    @Test
    @DisplayName("手工填公摊度数:直接用它,仍按各户实际用量占比摊;留空才自动算")
    void manualPublicUsageOverridesPool() {
        List<SheetRow> rows = List.of(
                row(1, "1层A区", "云帕", 1L, "电", "8003.4", true, null),
                row(2, "1层A区", "云帕", 1L, "电", "19200", true, null),
                row(3, "2层A区", "云山快仓", 2L, "电", "14481.6", true, null),
                row(4, "室外", "充电柱", null, "电", "364", false, null));
        UtilityResult r = FeeNoticeCalculator.allocate("电", new BigDecimal("176300"), new BigDecimal("1.03"), rows,
                new BigDecimal("33762.8"));

        assertThat(r.publicUsage()).isEqualByComparingTo("33762.80");
        BigDecimal shared = r.rows().stream().map(SheetRow::getPublicUsage).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shared).isEqualByComparingTo("33762.80");
        // 云帕两块表合计用量 27203.4,占参与公摊用量 41685 的份额 × 手工公摊度数
        BigDecimal expected = new BigDecimal("27203.4").multiply(new BigDecimal("33762.8"))
                .divide(new BigDecimal("41685"), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal yunpa = r.rows().get(0).getPublicUsage().add(r.rows().get(1).getPublicUsage());
        assertThat(yunpa.subtract(expected).abs()).isLessThan(new BigDecimal("0.02"));
    }

    @Test
    @DisplayName("分表合计超过总度数时公摊按 0 并给出警告")
    void negativePoolIsClampedWithWarning() {
        UtilityResult r = FeeNoticeCalculator.allocate("电", new BigDecimal("100"), BigDecimal.ONE,
                new ArrayList<>(List.of(row(1, "1层A区", "甲", 1L, "电", "150", true, null))));
        assertThat(r.publicUsage()).isEqualByComparingTo("0");
        assertThat(r.warnings()).anyMatch(w -> w.contains("超过总度数"));
    }

    @Test
    @DisplayName("通知单按 租户×区域 出,2层A/B/C区各一张不合并;垃圾费按各区计租面积分摊,不参与公摊的户不摊垃圾")
    void noticesGroupByTenantAndUnit() {
        FeePeriod p = new FeePeriod();
        p.setId(9L);
        p.setPeriod("2026-10");
        p.setNoticePrefix("DIPARK");
        p.setDomesticGarbageAmount(new BigDecimal("2402.78"));
        p.setDomesticGarbageNote("6桶*单价13.35元/桶");
        p.setIndustrialGarbageAmount(new BigDecimal("1854"));

        List<SheetRow> water = new ArrayList<>(List.of(
                row(10, "2层A区", "云山快仓", 2L, "水", "140", true, "4700"),
                row(11, "2层B区", "云山快仓", 2L, "水", "15", true, "4100"),
                row(12, "2层C区", "云山快仓", 2L, "水", "18", true, "4700"),
                row(13, "1层A区", "云帕", 1L, "水", "19", true, "3300")));
        List<SheetRow> electric = new ArrayList<>(List.of(
                row(20, "2层A区", "云山快仓", 2L, "电", "14481.6", true, "4700"),
                row(21, "1层A区", "云帕", 1L, "电", "8003.4", true, "3300"),
                row(22, "1层A区", "云帕", 1L, "电", "19200", true, "3300"),
                row(23, "室外", "充电柱", null, "电", "364", false, null)));
        UtilityResult wr = FeeNoticeCalculator.allocate("水", new BigDecimal("300"), new BigDecimal("5.51"), water);
        UtilityResult er = FeeNoticeCalculator.allocate("电", new BigDecimal("50000"), new BigDecimal("1.03"), electric);

        NoticeBatch batch = FeeNoticeCalculator.buildNotices(p, wr, er, Map.of());

        assertThat(batch.notices()).extracting(FeeNotice::getUnitName)
                .containsExactly("1层A区", "2层A区", "2层B区", "2层C区", "室外");
        assertThat(batch.totalLeaseArea()).isEqualByComparingTo("16800");
        FeeNotice yunpa = batch.notices().get(0);
        assertThat(yunpa.getNoticeNo()).isEqualTo("DIPARK-202610-1F-01");
        assertThat(yunpa.getElectricUsage()).isEqualByComparingTo("27203.4");
        // 两块电表 → 电费主行 + 两条子行,子行不计金额
        List<FeeNoticeItem> electricItems = yunpa.getItems().stream()
                .filter(i -> i.getItemType().startsWith("ELECTRIC") && !i.getItemType().endsWith("PUBLIC")).toList();
        assertThat(electricItems).hasSize(3);
        assertThat(electricItems.get(1).getSeq()).isEqualTo("3.1");
        assertThat(electricItems.get(1).getAmount()).isNull();
        // 生活垃圾 = 2402.78 × 3300 / 16800
        assertThat(yunpa.getDomesticGarbageFee()).isEqualByComparingTo("471.97");
        assertThat(yunpa.getIndustrialGarbageFee()).isEqualByComparingTo("364.18");

        // 2层三个区各自一张通知单,各自用量、各自面积分摊
        FeeNotice a = batch.notices().get(1);
        FeeNotice b = batch.notices().get(2);
        FeeNotice c = batch.notices().get(3);
        assertThat(a.getNoticeNo()).isEqualTo("DIPARK-202610-2F-01");
        assertThat(b.getNoticeNo()).isEqualTo("DIPARK-202610-2F-02");
        assertThat(c.getNoticeNo()).isEqualTo("DIPARK-202610-2F-03");
        assertThat(a.getWaterUsage()).isEqualByComparingTo("140");
        assertThat(b.getWaterUsage()).isEqualByComparingTo("15");
        assertThat(c.getWaterUsage()).isEqualByComparingTo("18");
        assertThat(a.getLeaseArea()).isEqualByComparingTo("4700");
        assertThat(b.getLeaseArea()).isEqualByComparingTo("4100");
        // 生活垃圾 2402.78 × 4100 / 16800 = 586.3866 → 586.39
        assertThat(b.getDomesticGarbageFee()).isEqualByComparingTo("586.39");
        assertThat(a.getSubtotal()).isEqualByComparingTo(
                a.getWaterFee().add(a.getWaterPublicFee()).add(a.getElectricFee())
                        .add(a.getElectricPublicFee()).add(a.getDomesticGarbageFee())
                        .add(a.getIndustrialGarbageFee()));

        FeeNotice pile = batch.notices().get(4);
        assertThat(pile.getUnitName()).isEqualTo("室外");
        assertThat(pile.getSharePublic()).isZero();
        assertThat(pile.getDomesticGarbageFee()).isEqualByComparingTo("0");
        assertThat(pile.getItems()).noneMatch(i -> i.getItemType().startsWith("GARBAGE"));
        assertThat(pile.getNoticeNo()).isEqualTo("DIPARK-202610-室外-01");
        assertThat(batch.warnings()).anyMatch(w -> w.contains("没有关联到在租合同"));
        assertThat(batch.warnings()).noneMatch(w -> w.contains("没有填计租面积"));
        // 通知单上的垃圾费之和恰好等于两笔总额
        assertThat(batch.notices().stream().map(FeeNotice::getDomesticGarbageFee).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("2402.78");
        assertThat(batch.notices().stream().map(FeeNotice::getIndustrialGarbageFee).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1854");
    }

    @Test
    @DisplayName("重新生成时找回手工新增的收费行和手改的备注,其他费用 = 手工行金额之和")
    void regenerateKeepsManualItemsAndRemarks() {
        FeePeriod p = new FeePeriod();
        p.setId(1L);
        p.setPeriod("2026-10");
        List<SheetRow> electric = new ArrayList<>(List.of(row(1, "1层A区", "云帕", 1L, "电", "100", true, "3300")));
        UtilityResult er = FeeNoticeCalculator.allocate("电", new BigDecimal("120"), BigDecimal.ONE, electric);
        UtilityResult wr = UtilityResult.empty("水", BigDecimal.ONE);

        FeeNotice first = FeeNoticeCalculator.buildNotices(p, wr, er, Map.of()).notices().get(0);
        // 用户在调整弹窗里:电费行改备注 + 新增两行(一行数量×单价,一行减免)
        FeeNoticeItem electricRow = first.getItems().stream().filter(i -> "ELECTRIC".equals(i.getItemType())).findFirst().orElseThrow();
        electricRow.setRemark("手工备注:含上月补抄");
        electricRow.setRemarkEdited(1);
        FeeNoticeItem fee = new FeeNoticeItem();
        fee.setItemType(FeeNoticeItem.T_CUSTOM);
        fee.setManual(1);
        fee.setFeeItem("设备维修费");
        fee.setAmount(new BigDecimal("200.00"));
        FeeNoticeItem discount = new FeeNoticeItem();
        discount.setItemType(FeeNoticeItem.T_CUSTOM);
        discount.setManual(1);
        discount.setFeeItem("减免");
        discount.setAmount(new BigDecimal("-50.00"));
        first.getItems().add(fee);
        first.getItems().add(discount);

        FeeNotice second = FeeNoticeCalculator.buildNotices(p, wr, er,
                Map.of(FeeNoticeCalculator.noticeKey(first), first)).notices().get(0);
        assertThat(second.getItems()).filteredOn(FeeNoticeItem::isManualItem).extracting(FeeNoticeItem::getFeeItem)
                .containsExactly("设备维修费", "减免");
        assertThat(second.getOtherFee()).isEqualByComparingTo("150.00");
        assertThat(second.getOtherFeeNote()).isEqualTo("设备维修费、减免");
        assertThat(second.getItems()).filteredOn(i -> "ELECTRIC".equals(i.getItemType()))
                .extracting(FeeNoticeItem::getRemark).containsExactly("手工备注:含上月补抄");
        // 小计 = 电费 100 + 公摊 20 + 其他 150
        assertThat(second.getSubtotal()).isEqualByComparingTo("270.00");
        // 没手改过备注的行(公摊电费)仍是自动备注,不被旧文本覆盖
        assertThat(second.getItems()).filteredOn(i -> "ELECTRIC_PUBLIC".equals(i.getItemType()))
                .allMatch(i -> i.getRemarkEdited() == null);
    }

    @Test
    @DisplayName("重新生成时按户找回手工填写的已付/前期欠费/其他费用")
    void regenerateKeepsManualFields() {
        FeePeriod p = new FeePeriod();
        p.setId(1L);
        p.setPeriod("2026-10");
        List<SheetRow> electric = new ArrayList<>(List.of(row(1, "1层A区", "云帕", 1L, "电", "100", true, "3300")));
        UtilityResult er = FeeNoticeCalculator.allocate("电", new BigDecimal("120"), BigDecimal.ONE, electric);
        UtilityResult wr = UtilityResult.empty("水", BigDecimal.ONE);

        FeeNotice first = FeeNoticeCalculator.buildNotices(p, wr, er, Map.of()).notices().get(0);
        first.setPaidAmount(new BigDecimal("50"));
        first.setOtherFee(new BigDecimal("3.75"));
        first.setOtherFeeNote("税金");
        FeeNoticeCalculator.recalcTotals(first);

        FeeNotice second = FeeNoticeCalculator.buildNotices(p, wr, er,
                Map.of(FeeNoticeCalculator.noticeKey(first), first)).notices().get(0);
        assertThat(second.getPaidAmount()).isEqualByComparingTo("50");
        assertThat(second.getOtherFee()).isEqualByComparingTo("3.75");
        assertThat(second.getItems()).anyMatch(i -> "OTHER".equals(i.getItemType()) && "税金".equals(i.getFeeItem()));
        // 小计 100 + 20 公摊 + 3.75 = 123.75;应收 = 123.75 − 50
        assertThat(second.getSubtotal()).isEqualByComparingTo("123.75");
        assertThat(second.getTotalDue()).isEqualByComparingTo("73.75");
    }

    @Test
    @DisplayName("最大余数法:平均分三份 100 元,三份相加恰好 100.00")
    void apportionSumsExactly() {
        List<BigDecimal> parts = FeeNoticeCalculator.apportion(new BigDecimal("100"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThat(parts).extracting(BigDecimal::toPlainString).containsExactly("33.34", "33.33", "33.33");
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(FeeNoticeCalculator.apportion(new BigDecimal("50"), List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
                .allMatch(x -> x.signum() == 0);
    }

    @Test
    @DisplayName("垃圾费按单元面积分摊:不参与的单元不摊,各单元合计等于总额")
    void garbageSplitSumsToTotal() {
        FeePeriod p = new FeePeriod();
        p.setDomesticGarbageAmount(new BigDecimal("2402.78"));
        p.setIndustrialGarbageAmount(new BigDecimal("1854"));
        List<SheetRow> rows = List.of(
                row(1, "1层A区", "甲", 1L, "电", "1", true, "3300"),
                row(2, "1层B区", "乙", 2L, "电", "1", true, "3600"),
                row(3, "1层C区", "丙", 3L, "电", "1", true, "3500"),
                row(4, "室外", "充电柱", null, "电", "1", false, "999"));
        FeeNoticeCalculator.GarbageSplit split = FeeNoticeCalculator.splitGarbage(p, rows);

        assertThat(split.totalArea()).isEqualByComparingTo("10400");
        assertThat(split.units().get("室外").domestic()).isEqualByComparingTo("0");
        BigDecimal domestic = split.units().values().stream().map(FeeNoticeCalculator.UnitShare::domestic)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal industrial = split.units().values().stream().map(FeeNoticeCalculator.UnitShare::industrial)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(domestic).isEqualByComparingTo("2402.78");
        assertThat(industrial).isEqualByComparingTo("1854");
    }

    @Test
    @DisplayName("楼层与单元名解析:数字/中文楼层、连续与不连续分区")
    void floorAndUnitNaming() {
        assertThat(FeeNoticeCalculator.floorKey("2层A区")).isEqualTo("2");
        assertThat(FeeNoticeCalculator.floorKey("八楼B区")).isEqualTo("8");
        assertThat(FeeNoticeCalculator.floorKey("室外")).isEqualTo("室外");
        assertThat(FeeNoticeCalculator.unitName("2", new java.util.LinkedHashSet<>(List.of("2层A区", "2层B区", "2层C区"))))
                .isEqualTo("2层A~C区");
        assertThat(FeeNoticeCalculator.unitName("5", new java.util.LinkedHashSet<>(List.of("5层A区", "5层C区"))))
                .isEqualTo("5层A、C区");
    }
}
