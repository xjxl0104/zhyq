package com.zhyq.park.marketing.service;

import com.zhyq.park.marketing.entity.MktPromoter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 后台账号与 P4 伙伴的匹配、以及“为什么只读”的提示。 */
class MktAdminActorAndReadOnlyReasonTest {
    private static MktPromoter promoter(String name, String position, int status) {
        MktPromoter p = new MktPromoter();
        p.setName(name); p.setPositionCode(position); p.setStatus(status);
        return p;
    }

    @Test
    void 只读原因逐项说明() {
        MktPromoter ding = promoter("丁超", "P4", 1);
        MktPromoter partner = promoter("乙", "P2", 1);
        assertThat(MktCustomerPricingService.readOnlyReason(null, partner)).contains("未关联伙伴档案");
        assertThat(MktCustomerPricingService.readOnlyReason(promoter("张三", "P3", 1), partner)).contains("张三").contains("P3").contains("仅 P4");
        assertThat(MktCustomerPricingService.readOnlyReason(promoter("丁超", "P4", 0), partner)).contains("不是正常状态");
        assertThat(MktCustomerPricingService.readOnlyReason(ding, null)).contains("没有成交伙伴");
        assertThat(MktCustomerPricingService.readOnlyReason(ding, partner)).contains("乙").contains("不在").contains("丁超");
    }
}
