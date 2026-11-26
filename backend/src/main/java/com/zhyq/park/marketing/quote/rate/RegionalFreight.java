package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static java.math.BigDecimal.ZERO;

/** Freight is quoted nationally: span the applicable provinces and volume tiers, without guessing a destination mix. */
final class RegionalFreight {
    private static final List<String> STANDARD = QuoteCard.WEIGHTS.stream().map(QuoteCard.Column::key).toList();
    private static final int[] JT_BANDS = {0,1,2,3,3,4,4};
    private static final int[] POST_BANDS = {0,0,1,2,2,3,3};

    record Estimate(QuoteCalculator.Amount amount,boolean coveragePartial) {}

    static Estimate price(QuoteCard card, String table, JsonNode demand, List<String> missing, List<String> notes) {
        var weights = new ArrayList<String>();
        demand.path("weightRanges").forEach(n -> weights.add(n.asText()));
        if (weights.isEmpty()) { missing.add("客户未勾选重量档"); return new Estimate(null,true); }
        BigDecimal heavyKg = demand.path("heavyWeightKg").isNumber() ? demand.path("heavyWeightKg").decimalValue() : null;
        if (weights.contains(">3") && (heavyKg == null || heavyKg.compareTo(BigDecimal.valueOf(3)) <= 0)) {
            missing.add("超过3kg需提供典型重量"); return new Estimate(null,true);
        }
        var daily = QuoteCalculator.dailyBounds(demand.path("dailyOrders").asText(""));
        List<BigDecimal> known = new ArrayList<>();
        int applicable = 0, complete = 0;
        for (var row : QuoteTariff.section(table).rows()) {
            if (!overlaps(row,daily)) continue;
            applicable++;
            var values = card.tariff().getOrDefault(row.id(),Map.of());
            var prices = new ArrayList<BigDecimal>();
            for (String weight : weights) {
                int band = STANDARD.indexOf(weight);
                if (band < 0 && !weight.equals(">3")) continue;
                if ("sf".equals(table) && band >= 0) {
                    BigDecimal[] bracket = Arrays.stream(weight.split("-")).map(BigDecimal::new).toArray(BigDecimal[]::new);
                    // Minimum billable weight strictly exceeds the lower endpoint of a selected open-left band.
                    var lo = sf(values,bracket[0].add(new BigDecimal("0.0001")));
                    var hi = sf(values,bracket[1]);
                    if (lo == null || hi == null) break;
                    prices.add(lo.min(hi)); prices.add(lo.max(hi));
                } else {
                    BigDecimal v = band < 0 ? heavy(values,table,heavyKg) : n(values,"w"+switch (table) {
                        case "jt" -> JT_BANDS[band]; case "post" -> POST_BANDS[band]; default -> band;
                    });
                    if (v == null) break;
                    prices.add(v);
                }
            }
            int required = weights.size() + ("sf".equals(table) ? (int) weights.stream().filter(STANDARD::contains).count() : 0);
            if (prices.size() == required) { complete++; known.addAll(prices); }
        }
        if (complete < applicable) missing.add("适用地区或日单档仅 "+complete+"/"+applicable+" 行有完整运价，区间仅覆盖已报价行");
        boolean restrictedGeography="sf".equals(table);
        if (restrictedGeography) {
            missing.add("顺丰模板未列西藏和青海玉树运价，全国配送需另行询价");
            notes.add("顺丰按表内始发地、目的地区域和日均票量分档；20kg以上需另询，高峰附加及资源调节费另计。");
        }
        notes.add("快递区间取所选重量在表内中国大陆地区已报价的最低、最高值，港澳台另询；泡重、偏远流向及附加费用按条款核定。");
        return new Estimate(known.isEmpty() ? null : QuoteCalculator.Amount.bounds(known),complete<applicable||restrictedGeography);
    }

    private static boolean overlaps(QuoteTariff.Row row, BigDecimal[] orders) {
        return (row.maxOrders()==null || orders[0].compareTo(BigDecimal.valueOf(row.maxOrders()))<=0)
                && (orders[1]==null || row.minOrders()==null || orders[1].compareTo(BigDecimal.valueOf(row.minOrders()))>=0);
    }

    private static BigDecimal heavy(Map<String,String> values,String table,BigDecimal weight) {
        if ("sf".equals(table)) return sf(values,weight);
        BigDecimal kg = weight.setScale(0,RoundingMode.CEILING);
        if ("post".equals(table)) {
            if (kg.compareTo(BigDecimal.TEN) <= 0) return n(values,"w"+kg.intValueExact());
            return basePlus(n(values,"w10"),n(values,"w11"),kg.subtract(BigDecimal.TEN));
        }
        return basePlus(n(values,"label"),n(values,"kg"),kg);
    }

    private static BigDecimal sf(Map<String,String> values,BigDecimal weight) {
        if (weight.compareTo(BigDecimal.valueOf(20))>0) return null;
        boolean heavy = weight.compareTo(BigDecimal.valueOf(3))>0;
        var initial = heavy ? new BigDecimal("3.1") : BigDecimal.ONE;
        var increment = new BigDecimal("0.5");
        var extra = weight.subtract(initial).max(ZERO).divide(increment,0,RoundingMode.CEILING).multiply(increment);
        return basePlus(n(values,heavy?"heavyFirst":"first"),n(values,heavy?"heavyStep":"step"),extra);
    }

    static BigDecimal n(Map<String,String> cells,String key) {
        String value=cells.get(key);
        return value==null ? null : new BigDecimal(value);
    }
    private static BigDecimal basePlus(BigDecimal base,BigDecimal rate,BigDecimal units) {
        if (base==null) return null;
        if (units.signum()==0) return base;
        return rate==null?null:base.add(units.multiply(rate));
    }
}
