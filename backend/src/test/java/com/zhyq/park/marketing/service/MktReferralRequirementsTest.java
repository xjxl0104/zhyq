package com.zhyq.park.marketing.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MktReferralRequirementsTest {
    @Test void optionalFieldsAndUnknownInvoiceRemainUnset() {
        assertThat(MktReferralRequirements.normalize(null)).isNull();
        var value = MktReferralRequirements.parse(MktReferralRequirements.normalize(Map.of()));
        assertThat(value.get("invoiceRequired")).isNull();
        assertThat(value.get("productWeights")).isEqualTo(List.of());
        assertThat(value.get("productCategories")).isEqualTo(List.of());
        assertThat(value.get("productCategoryOther")).isNull();
        assertThat(value.get("itemsPerParcelRange")).isNull();
    }

    @Test void savesAllProductCategoriesAndTrimsOtherText() {
        var categories = List.of("食品饮料", "美妆护肤", "日化清洁", "服装鞋帽", "家居家纺", "母婴用品",
                "宠物用品", "数码家电", "文体办公", "箱包配饰", "五金汽配", "其他");
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(Map.of(
                "productCategories", categories, "productCategoryOther", " 绿植盆栽 ", "itemsPerParcelRange", "2-5")));
        assertThat(saved.get("productCategories")).isEqualTo(categories);
        assertThat(saved.get("productCategoryOther")).isEqualTo("绿植盆栽");
        assertThat(saved.get("itemsPerParcelRange")).isEqualTo("2-5");
        // A complete normalized payload must still fit the shared input-field limit.
        assertThat(saved).hasSizeLessThanOrEqualTo(30);
        assertThat(MktReferralRequirements.parse(MktReferralRequirements.normalize(saved))).isEqualTo(saved);
    }

    @Test void requiresValidDistinctProductCategoriesAndBoundedOtherText() {
        for (var input : List.<Map<String, Object>>of(
                Map.of("productCategories", "食品饮料"),
                Map.of("productCategories", List.of("未知品类")),
                Map.of("productCategories", List.of(1)),
                Map.of("productCategories", List.of("食品饮料", "食品饮料")),
                Map.of("productCategories", List.of("其他")),
                Map.of("productCategories", List.of("其他"), "productCategoryOther", "  "),
                Map.of("productCategories", List.of("其他"), "productCategoryOther", "x".repeat(101)))) {
            assertThatThrownBy(() -> MktReferralRequirements.normalize(input))
                    .isInstanceOf(com.zhyq.park.common.exception.BizException.class).hasMessageContaining("销售品类");
        }
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(Map.of(
                "productCategories", List.of("食品饮料"), "productCategoryOther", "旧的自填内容")));
        assertThat(saved.get("productCategoryOther")).isNull();
    }

    @Test void acceptsParcelRangesAndRejectsInvalidSelections() {
        for (var range : List.of("0-2", "2-5", "5-10", "10-20", ">=20")) {
            var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(Map.of("itemsPerParcelRange", range)));
            assertThat(saved.get("itemsPerParcelRange")).isEqualTo(range);
        }
        for (var invalid : List.of("0", "4-5", 2, List.of("0-2", "2-5"))) {
            assertThatThrownBy(() -> MktReferralRequirements.normalize(Map.of("itemsPerParcelRange", invalid)))
                    .isInstanceOf(com.zhyq.park.common.exception.BizException.class).hasMessageContaining("平均包裹产品数量");
        }
    }

    @Test void keepsLegacyCountsAndParcelNumbersAlongsideNewSelections() {
        var legacy = MktReferralRequirements.parse("{\"productCount\":\"10-20\",\"itemsPerParcel\":1.5}");
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(legacy));
        assertThat(saved.get("productCount")).isEqualTo("10-20");
        assertThat(saved.get("itemsPerParcel").toString()).isEqualTo("1.5");
        assertThat(saved.get("productCategories")).isEqualTo(List.of());
        assertThat(saved.get("itemsPerParcelRange")).isNull();
        legacy.put("productCategories", List.of("美妆护肤"));
        legacy.put("itemsPerParcelRange", "0-2");
        var updated = MktReferralRequirements.parse(MktReferralRequirements.normalize(legacy));
        assertThat(updated.get("productCount")).isEqualTo("10-20");
        assertThat(updated.get("itemsPerParcel").toString()).isEqualTo("1.5");
        assertThat(updated.get("productCategories")).isEqualTo(List.of("美妆护肤"));
        assertThat(updated.get("itemsPerParcelRange")).isEqualTo("0-2");
    }
    @Test void preservesMultiplePlatformsOtherCarrierAndCompleteWeightDistribution() {
        var input = Map.of("salesPlatforms", List.of("天猫", "其他"), "salesPlatformOther", " 私域商城 ",
                "couriers", List.of("中通", "其他"), "courierOther", "京东", "invoiceRequired", true,
                "productWeights", List.of(Map.of("range", "0-0.3", "percentage", 30), Map.of("range", ">=3", "percentage", 70)),
                "storageArea", ">=5000", "skuCount", ">=500");
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(input));
        assertThat(saved.get("salesPlatformOther")).isEqualTo("私域商城");
        assertThat(saved.get("courierOther")).isEqualTo("京东");
        assertThat(saved.get("invoiceRequired")).isEqualTo(true);
        assertThat((List<?>) saved.get("productWeights")).hasSize(2);
    }
    @Test void weightRangesCanBeSelectedWithoutPercentages() {
        assertThat(MktReferralRequirements.normalize(Map.of("productWeights", List.of(Map.of("range", "0-0.3"), Map.of("range", ">=3"))))).isNotNull();
    }
    @Test void rejectsPartialOverfullAndDuplicateWeightDistribution() {
        for (var rows : List.of(
                List.of(Map.of("range", "0-0.3", "percentage", 20), Map.of("range", ">=3")),
                List.of(Map.of("range", "0-0.3", "percentage", 30), Map.of("range", ">=3", "percentage", 80)),
                List.of(Map.of("range", ">=3"), Map.of("range", ">=3")))) {
            assertThatThrownBy(() -> MktReferralRequirements.normalize(Map.of("productWeights", rows))).isInstanceOf(com.zhyq.park.common.exception.BizException.class);
        }
    }
    @Test void keepsContactPositionItemsPerParcelAndCurrentLogisticsStatus() {
        var input = new HashMap<String, Object>();
        input.put("contactPosition", " 运营总监 "); input.put("itemsPerParcel", "1.5");
        input.put("couriers", List.of("京东", "邮政")); input.put("warehouseMode", "外包云仓");
        input.put("currentCouriers", List.of("圆通", "其他")); input.put("currentCourierOther", " 德邦 ");
        input.put("painPoints", List.of("大促爆仓", "系统对接难"));
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(input));
        assertThat(saved.get("contactPosition")).isEqualTo("运营总监");
        assertThat(saved.get("itemsPerParcel").toString()).isEqualTo("1.5");
        assertThat(saved.get("couriers")).isEqualTo(List.of("京东", "邮政"));
        assertThat(saved.get("warehouseMode")).isEqualTo("外包云仓");
        assertThat(saved.get("currentCourierOther")).isEqualTo("德邦");
        assertThat(saved.get("painPoints")).isEqualTo(List.of("大促爆仓", "系统对接难"));
    }
    @Test void rejectsInvalidCurrentStatusAndItemsPerParcel() {
        for (var input : List.<Map<String, Object>>of(Map.of("warehouseMode", "乱填"), Map.of("painPoints", List.of("其他")),
                Map.of("currentCouriers", List.of("其他")), Map.of("itemsPerParcel", 0), Map.of("itemsPerParcel", "1.234"),
                Map.of("itemsPerParcel", "abc"), Map.of("contactPosition", "x".repeat(51)))) {
            assertThatThrownBy(() -> MktReferralRequirements.normalize(input)).isInstanceOf(com.zhyq.park.common.exception.BizException.class);
        }
    }
    @Test void rejectsUnknownRangesTypesAndOtherWithoutText() {
        for (var input : List.of(Map.of("salesPlatforms", List.of("其他")), Map.of("couriers", List.of("其他")),
                Map.of("storageArea", "bogus"), Map.of("invoiceRequired", "false"), Map.of("packaging", "x".repeat(501)))) {
            assertThatThrownBy(() -> MktReferralRequirements.normalize(input)).isInstanceOf(com.zhyq.park.common.exception.BizException.class);
        }
    }
    @Test void noneCourierIsExclusive() {
        var saved = MktReferralRequirements.parse(MktReferralRequirements.normalize(Map.of("couriers", List.of("无"), "currentCouriers", List.of("无"))));
        assertThat(saved.get("couriers")).isEqualTo(List.of("无"));
        for (var key : List.of("couriers", "currentCouriers"))
            assertThatThrownBy(() -> MktReferralRequirements.normalize(Map.of(key, List.of("无", "圆通")))).hasMessageContaining("无");
    }
}
