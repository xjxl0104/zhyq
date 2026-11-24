package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

final class QuoteTariff {
    record Column(String key, String label, String kind) {}
    record Row(String id, String label, Integer minOrders, Integer maxOrders, String service, String unit) {}
    record Section(String key, String label, List<Column> columns, List<Row> rows) {}
    static final List<Section> SECTIONS = load();
    static final Map<String, Section> BY_ROW = new HashMap<>();
    static final Map<String, String> CARRIERS = Map.of("圆通","yt","中通","zt","极兔","jt","顺丰","sf","邮政","post");
    static {
        for (Section section : SECTIONS) {
            for (Row row : section.rows()) BY_ROW.put(row.id(), section);
        }
    }

    private static List<Section> load() {
        try (var input = QuoteTariff.class.getResourceAsStream("/warehouse-quote-template.json")) {
            if (input == null) throw new IllegalStateException("缺少云仓报价模板");
            return new ObjectMapper().readValue(input, new TypeReference<>() {});
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Map<String, Map<String,String>> parse(JsonNode input) {
        Map<String,Map<String,String>> result = new LinkedHashMap<>();
        if (input.isMissingNode() || input.isNull()) return result;
        if (!input.isObject()) throw QuoteCard.bad("详细报价格式错误");
        input.fields().forEachRemaining(entry -> {
            var section = BY_ROW.get(entry.getKey());
            var line = entry.getValue();
            if (section == null || !line.isObject()) throw QuoteCard.bad("详细报价项目不在模板内");
            var values = new LinkedHashMap<String,String>();
            line.fields().forEachRemaining(cell -> {
                var col = section.columns().stream().filter(c -> c.key().equals(cell.getKey())).findFirst()
                        .orElseThrow(() -> QuoteCard.bad("详细报价列不在模板内"));
                JsonNode value = cell.getValue();
                if (value.isNull() || value.isTextual() && value.asText().isBlank()) return;
                if (col.kind().equals("text")) {
                    if (!value.isTextual() || value.asText().length() > 2000) throw QuoteCard.bad("报价说明最多2000字");
                    values.put(col.key(), value.asText().trim());
                } else {
                    var price = QuoteCard.number(value);
                    if (price != null) values.put(col.key(), price.toPlainString());
                }
            });
            if (!values.isEmpty()) {
                validateLine(entry.getKey(),section,values);
                result.put(entry.getKey(), values);
            }
        });
        return result;
    }

    private static void validateLine(String id,Section section,Map<String,String> values) {
        if ("stage".equals(section.key()) && values.containsKey("discount")) {
            var price=RegionalFreight.n(values,"bundle");
            var costs=List.of("express","handling","box","consumable");
            if (price==null && values.keySet().containsAll(costs))
                price=costs.stream().map(k -> RegionalFreight.n(values,k)).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
            if (price!=null && RegionalFreight.n(values,"discount").compareTo(price)>0) throw QuoteCard.bad("阶段报价的减免金额不能高于原金额");
        }
        if (Set.of("conditions.batchRatio","conditions.sampling").contains(id) && values.containsKey("value")
                && RegionalFreight.n(values,"value").compareTo(java.math.BigDecimal.valueOf(100))>0)
            throw QuoteCard.bad("比例不能超过100%");
    }

    static Section section(String key) {
        return SECTIONS.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }
    static boolean hasSection(QuoteCard card, String key) {
        return section(key).rows().stream().anyMatch(r -> card.tariff().containsKey(r.id()));
    }
}
