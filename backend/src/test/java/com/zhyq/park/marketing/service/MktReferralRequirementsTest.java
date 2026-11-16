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
