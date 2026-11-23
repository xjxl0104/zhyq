package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuoteRequirementsTest {
    @Test void preservesZeroNationalMultiCarrierAndIndependentQuoteChoices() {
        var raw=Map.<String,Object>of("couriers",List.of("中通","圆通"),
            "materialProviders",List.of("客户提供","云仓提供"),"services",List.of("退件回仓上架","拼箱调拨或退仓"),
            "serviceQuantities",Map.of("returns","约10单/月，另拼箱2箱"),"palletCount","0","batchPercent","0",
            "weightRanges",List.of("2.5-3",">3"),"heavyWeightKg","3.25","invoice","不开票","tenantId",123);
        var out=QuoteRequirements.normalize(raw);
        assertEquals(List.of("圆通","中通"),out.get("couriers"));
        assertEquals(BigDecimal.ZERO,out.get("palletCount"));
        assertEquals(new BigDecimal("3.25"),out.get("heavyWeightKg"));
        assertFalse(out.containsKey("tenantId"));
        assertEquals(Map.of("returns","约10单/月，另拼箱2箱"),out.get("serviceQuantities"));
    }
    @Test void rejectsConflictsInvalidOptionsAndMissingOtherNames() {
        for (var raw : List.of(Map.of(),Map.of("invoice","专票6%"),Map.of("batchPercent",101),
                Map.of("peakOrders","30000"),Map.of("weightRanges",List.of(">3"),"heavyWeightKg","3.000"),
                Map.of("itemsPerParcel","1.2"),Map.of("materialProviders",List.of("仓","仓")),
                Map.of("salesPlatforms",List.of("其他")),Map.of("serviceQuantities",Map.of("returns","5吨")),
                Map.of("erpSystems",List.of("用友")),Map.of("couriers",List.of("无","顺丰")))) {
            assertThrows(BizException.class,()->QuoteRequirements.normalize(raw),raw.toString());
        }
    }
    @Test void canonicalizesSelectionsAndHonorsUpperRanges() {
        assertEquals(QuoteRequirements.normalize(Map.of("skuCount",">=500","erpSystems",List.of("万里牛","旺店通"))),
                QuoteRequirements.normalize(Map.of("erpSystems",List.of("旺店通","万里牛"),"skuCount",">=500")));
        assertEquals(">=50000",QuoteRequirements.normalize(Map.of("peakOrders",">=50000")).get("peakOrders"));
        assertEquals(27,QuoteRequirements.SERVICES.size());
    }
}
