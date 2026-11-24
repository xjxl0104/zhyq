package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalTariffTest {
    private final ObjectMapper json=new ObjectMapper();

    private ObjectNode card(){
        var n=json.createObjectNode();
        n.put("scopeNote","全国 · 价格以对应地区和单量档为准");
        n.putArray("carriers");
        return n;
    }
    private ObjectNode demand(String weight){
        var n=json.createObjectNode();n.putArray("weightRanges").add(weight);
        n.put("invoice","不开票");n.put("dailyOrders","200-1000");return n;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> unit(Map<String,Object> offer){return (Map<String,Object>)offer.get("unitPrices");}

    @Test void yunshanWorkbookKeepsBlankPricesAndPerCarrierGeographies() throws Exception {
        var in=getClass().getResourceAsStream("/db/migration/V133__yunshan_detailed_tariff.sql");
        assertNotNull(in);
        String sql;
        try(in){sql=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String literal=sql.substring(sql.indexOf("CAST('")+6,sql.indexOf("' AS JSON)")).replace("\\\\","\\").replace("''","'");
        var c=QuoteCard.parse(json.readTree(literal));
        assertEquals(31,QuoteTariff.section("yt").rows().size());
        assertEquals(18,QuoteTariff.section("sf").rows().size());
        assertEquals(31,QuoteTariff.section("post").rows().size());
        assertEquals(16,QuoteTariff.section("packaging").rows().size());
        assertFalse(QuoteTariff.hasSection(c,"packaging"));
        var d=demand("0-0.3");d.putArray("couriers").add("圆通");
        var offer=QuoteCalculator.offers(c,d,500).get(0);
        assertEquals("uncertain",offer.get("capacityFit"));
        assertEquals(new BigDecimal("1.6000"),unit(offer).get("freightMin"));
        assertEquals(new BigDecimal("15.0000"),unit(offer).get("freightMax"));
        assertNull(offer.get("perParcelMin"));
        assertFalse(offer.containsKey("stageRates"));
        d.put("itemsPerParcel",3).put("batchPercent",80).put("dailyOrders","5000-10000").put("palletCount",100);
        d.putArray("materialProviders").add("客户提供");
        var full=QuoteCalculator.offers(c,d,20000).get(0);
        assertEquals(new BigDecimal("1.9000"),full.get("perParcelMin"));
        assertEquals(new BigDecimal("15.3000"),full.get("perParcelMax"));
        assertEquals(new BigDecimal("0.0000"),full.get("storageMonthly"));
        d.put("invoice","开增值税专票");
        var invoiced=QuoteCalculator.offers(c,d,20000).get(0);
        assertEquals(new BigDecimal("2.0140"),invoiced.get("perParcelMin"));
        assertEquals(new BigDecimal("16.2180"),invoiced.get("perParcelMax"));
    }
    @Test void postalUsesTenKgRatePlusRoundedExcessNotAnInventedNationalAverage() {
        var c=card();
        c.putObject("tariff").putObject("post.164").put("w10",16).put("w11","2.5");
        var d=demand(">3");d.put("heavyWeightKg",10.01);
        var offer=QuoteCalculator.offers(QuoteCard.parse(c),d,50).get(0);
        assertEquals("邮政",offer.get("carrier"));assertEquals("exceeded",offer.get("capacityFit"));
        assertEquals(new BigDecimal("18.5000"),unit(offer).get("freightMin"));
        assertTrue(offer.get("missing").toString().contains("1/31"));
        assertEquals(true,offer.get("regionalCoveragePartial"));
    }
    @Test void sfMatchesOrderBracketAndProvinceThenUsesHalfKgSteps() {
        var c=card();var rates=c.putObject("tariff");
        rates.putObject("sf.135").put("first",100);
        rates.putObject("sf.136").put("first","11").put("step","1.5");
        rates.putObject("sf.145").put("first","12").put("step","2");
        var d=demand("1-1.5");d.put("dailyOrders","200-300");
        var offer=QuoteCalculator.offers(QuoteCard.parse(c),d,300).get(0);
        assertEquals("fits",offer.get("capacityFit"));
        assertEquals(new BigDecimal("11.7500"),unit(offer).get("freightMin"));
        assertEquals(new BigDecimal("13.0000"),unit(offer).get("freightMax"));
    }
    @Test void stagePackageOfferedOncePerWarehouseOnlyWhenCustomerHasNotSpecifiedACourier() {
        var raw=card();
        raw.putObject("tariff").putObject("stage.12").put("bundle",3).put("discount","0.2");
        var card=QuoteCard.parse(raw);var demand=demand("0-0.3");
        assertEquals(new BigDecimal("2.8000"),QuoteCalculator.stageRates(card,demand).get(0).get("rate"));
        demand.putArray("couriers").add("无");
        assertEquals(1,QuoteCalculator.stageRates(card,demand).size());
        for(String carrier:List.of("圆通","顺丰","邮政","其他")){
            demand.putArray("couriers").add(carrier);
            assertTrue(QuoteCalculator.stageRates(card,demand).isEmpty());
            assertTrue(QuoteCalculator.offers(card,demand).stream().noneMatch(o->o.containsKey("stageRates")));
        }
    }
    @Test void fullyFilledSfTariffStillCannotRepresentNationwideCoverage() {
        var raw=card();var tariff=raw.putObject("tariff");
        for(var row:QuoteTariff.section("sf").rows())tariff.putObject(row.id()).put("first",10);
        var offer=QuoteCalculator.offers(QuoteCard.parse(raw),demand("0-0.3")).get(0);
        assertEquals(new BigDecimal("10.0000"),unit(offer).get("freightMin"));
        assertEquals(true,offer.get("regionalCoveragePartial"));
        assertTrue(offer.get("missing").toString().contains("青海玉树"));
    }
    @Test void serviceQuantityPromptUsesSameWorkbookUnitAsItsPrice() {
        var raw=card();raw.putObject("services").put("批次或残次品挑选","0.30");
        raw.putObject("tariff").putObject("services.35").put("note","按箱");
        var demand=demand("0-0.3");demand.putArray("services").add("批次或残次品挑选");
        var offer=QuoteCalculator.offers(QuoteCard.parse(raw),demand).get(0);
        assertTrue(offer.get("missing").toString().contains("批次或残次品挑选（元/箱）"));
    }
    @Test void unknownRowAndNegativeValuesRejectInsteadOfBecomingFree() {
        var c=card();var rates=c.putObject("tariff");rates.putObject("yt.6").put("w0","-1");
        assertThrows(RuntimeException.class,()->QuoteCard.parse(c));
        rates.removeAll();rates.putObject("invented.1").put("w0",0);
        assertThrows(RuntimeException.class,()->QuoteCard.parse(c));
        rates.removeAll();rates.putObject("yt.6").put("w0"," ");
        assertTrue(QuoteCard.parse(c).tariff().isEmpty());
    }
}
