package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QuoteCalculatorTest {
    private final ObjectMapper json=new ObjectMapper();
    private ObjectNode card() throws Exception {
        return (ObjectNode)json.readTree("""
          {"scopeNote":"全国普通地区，当月有效","fields":{"packSingle":0.6,"packBatch":0.3,"includedItems":3,"extraItem":0.1,
           "material":0.5,"storageDaily":2.5,"freeTickets":15,"highVolume":5000,"highStorageDaily":2,"highFreeTickets":12,
           "invoiceNormal":0,"invoiceSpecial":6},"services":{"入库抽检":0,"制标贴小标":0.2},
           "carriers":[{"name":"中通","rates":{"0-0.3":1.5,"2.5-3":2.8},"heavyLabelFee":1,"heavyPerKg":2,"scopeNote":"旺季另核"},
            {"name":"顺丰","rates":{"0-0.3":4,"2.5-3":8},"heavyRule":"firstStep","heavyFirstKg":3.1,"heavyFirstFee":11,"heavyStepKg":0.5,"heavyStepFee":1.5,"heavyMaxKg":20}]}
           """);
    }
    private ObjectNode demand()throws Exception{
        return (ObjectNode)json.readTree("""
            {"weightRanges":["0-0.3","2.5-3"],"itemsPerParcel":4,"batchPercent":80,"dailyOrders":"5000-10000",
              "palletCount":100,"materialProviders":["云仓提供"],"services":["入库抽检"],"invoice":"开增值税专票","couriers":["无"]}
            """);
    }
    private static void amount(Map<String,Object> row,String key,String n){assertThat((BigDecimal)row.get(key)).isEqualByComparingTo(n);}
    @Test void carrierAlternativesRemainSeparateAndTotalsIncludeTaxAndExtraPiece()throws Exception{
        var offers=QuoteCalculator.offers(QuoteCard.parse(card()),demand());
        assertThat(offers).extracting(o->o.get("carrier")).containsExactly("中通","顺丰");
        amount(offers.get(0),"perParcelMin","2.6076");amount(offers.get(0),"perParcelMax","3.9856");
        amount(offers.get(1),"perParcelMin","5.2576");amount(offers.get(1),"storageMonthly","0");
        assertThat(offers.get(0).get("status")).isEqualTo("estimate");
    }
    @Test void handlesThreeKgUpperBoundAndDifferentHeavyRulesAndCeilSteps()throws Exception{
        var c=QuoteCard.parse(card());var d=demand();
        d.put("invoice","不开票");d.put("batchPercent",100);d.putArray("materialProviders").add("客户提供");
        d.putArray("weightRanges").add("2.5-3");d.put("itemsPerParcel",1);
        amount(QuoteCalculator.offers(c,d).get(0),"perParcelMin","3.1");
        d.putArray("weightRanges").add(">3");d.put("heavyWeightKg",3.61);
        var aa=QuoteCalculator.offers(c,d);amount(aa.get(0),"perParcelMin","9.3");amount(aa.get(1),"perParcelMin","14.3");
        d.put("heavyWeightKg",20.1);
        assertThat(QuoteCalculator.offers(c,d).get(1).get("perParcelMin")).isNull();
    }
    @Test void missingIsDifferentFromExplicitlyFreeAndIncompleteTierDoesNotCreateFalseCheapestOption()throws Exception{
        var c=card();var d=demand();((ObjectNode)c.path("carriers").get(0).path("rates")).remove("2.5-3");
        d.putArray("couriers").add("中通").add("其他");d.put("courierOther","客户指定物流");
        var o=QuoteCalculator.offers(QuoteCard.parse(c),d);
        assertThat(o.get(0).get("perParcelMin")).isNull();assertThat(o.get(1).get("status")).isEqualTo("missing");
        assertThat(o.get(0).get("extrasMonthly")).isEqualTo(BigDecimal.ZERO);
        d.putArray("services").add("制标贴小标");((ObjectNode)d.putObject("serviceQuantities")).put("packing","贴标约100张");
        assertThat(QuoteCalculator.offers(QuoteCard.parse(c),d).get(0).get("extrasMonthly")).isNull();
    }
    @Test void monthlyStorageSpansBothSidesOfFreeThresholdAndChangesAt5000()throws Exception{
        var d=demand();d.put("palletCount",400);d.put("invoice","不开票");d.put("dailyOrders","3000-5000");
        var c=QuoteCard.parse(card());var r=QuoteCalculator.offers(c,d).get(0);
        amount(r,"storageMonthly","30000");
        d.put("dailyOrders","5000-10000");amount(QuoteCalculator.offers(c,d).get(0),"storageMonthly","0");
        d.put("palletCount",750);r=QuoteCalculator.offers(c,d).get(0);
        amount(r,"storageMonthlyMin","0");amount(r,"storageMonthlyMax","45000");assertThat(r.get("storageMonthly")).isNull();
    }
    @Test void bothMaterialSuppliersAndUndecidedTaxCannotPretendAllInQuote()throws Exception{
        var d=demand();d.putArray("materialProviders").add("客户提供").add("云仓提供");
        assertThat(QuoteCalculator.offers(QuoteCard.parse(card()),d).get(0).get("perParcelMin")).isNull();
        d.put("invoice","待确认");d.putArray("materialProviders").add("客户提供");
        assertThat(QuoteCalculator.offers(QuoteCard.parse(card()),d).get(0).get("perParcelMin")).isNull();
    }
    @Test void quoteTemplateHasAllApprovedServicesAndSevenLightWeightBands(){
        assertThat(QuoteCard.SERVICES.stream().map(QuoteCard.Column::key).distinct()).hasSize(27);
        assertThat(QuoteCard.WEIGHTS).hasSize(7);
    }
    @Test void knownPeakCapacityConstraintIsReportedAlongsidePrice()throws Exception{
        var demand=demand();demand.put("dailyOrders","0-200");demand.put("peakOrders","10000-30000");
        var offer=QuoteCalculator.offers(QuoteCard.parse(card()),demand,8000).get(0);
        assertThat(offer).containsEntry("dailyCapacity",8000);
        assertThat(offer.get("notes").toString()).contains("大促单量下限已超过").doesNotContain("日均单量下限已超过");
    }
    @Test void malformedNegativeUnknownAndDuplicateRowsAreRejected()throws Exception{
        var c=card();((ObjectNode)c.path("fields")).put("packSingle",-1);
        assertThatThrownBy(()->QuoteCard.parse(c)).isInstanceOf(BizException.class);
        var dupe=card();((ObjectNode)dupe.path("carriers").get(1)).put("name","中通");
        assertThatThrownBy(()->QuoteCard.parse(dupe)).isInstanceOf(BizException.class);
        var invalid=card();((ObjectNode)invalid.path("services")).put("不存在",1);
        assertThatThrownBy(()->QuoteCard.parse(invalid)).isInstanceOf(BizException.class);
    }
}
