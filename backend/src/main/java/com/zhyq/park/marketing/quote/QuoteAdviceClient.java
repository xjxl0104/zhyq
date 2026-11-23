package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Sends only anonymous numeric offers. Provider output can select existing offers and checklist items. */
@Component
public class QuoteAdviceClient {
    private final ObjectMapper json;
    private final String endpoint, key, model;
    private final boolean capped;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String,String> FOLLOW_UP = Map.of(
        "weights","确认各重量档的实际占比和重件重量",
        "materials","核实仓供包材规格与单价",
        "scope","核实偏远附加费、适用范围和有效期",
        "volume","核实大促产能与日均单量",
        "storage","核实板位占用与免租条件",
        "extras","按实际作业量核对增值服务费用",
        "tax","核实开票口径与含税价格");

    public QuoteAdviceClient(ObjectMapper json,
        @Value("${ZHYQ_QUOTE_AI_URL:}") String endpoint,
        @Value("${ZHYQ_QUOTE_AI_KEY:}") String key,
        @Value("${ZHYQ_QUOTE_AI_MODEL:}") String model,
        @Value("${ZHYQ_QUOTE_AI_DAILY_CAP_VERIFIED:false}") boolean capped) {
        this.json=json; this.endpoint=endpoint; this.key=key; this.model=model; this.capped=capped;
    }
    boolean configured() {
        if (!capped || key.isBlank() || model.isBlank()) return false;
        try {
            URI target=URI.create(endpoint);
            String host=target.getHost();
            return "https".equalsIgnoreCase(target.getScheme()) && host!=null && (host.equals("vvaix.com") || host.endsWith(".vvaix.com"))
                && target.getUserInfo()==null && target.getQuery()==null && target.getFragment()==null;
        } catch (IllegalArgumentException invalid) { return false; }
    }
    record Candidate(String label, Map<String,Object> numbers) {}
    List<Candidate> candidates(Map<String,Object> comparison) {
        var list=new ArrayList<Candidate>();
        for (JsonNode warehouse : json.valueToTree(comparison).path("warehouses")) {
            for (JsonNode offer : warehouse.path("offers")) {
                if (!offer.path("perParcelMin").isNumber() || !offer.path("perParcelMax").isNumber()) continue;
                var numbers=new LinkedHashMap<String,Object>();
                numbers.put("id",list.size()+1);
                for(String field:List.of("perParcelMin","perParcelMax","storageMonthlyMin","storageMonthlyMax","extrasMonthly")) {
                    if (offer.path(field).isNumber()) numbers.put(field,offer.path(field).decimalValue());
                }
                numbers.put("incomplete",!"estimate".equals(offer.path("status").asText()));
                list.add(new Candidate(warehouse.path("warehouseName").asText()+" · "+offer.path("carrier").asText(),numbers));
            }
        }
        return list;
    }
    List<String> assess(List<Candidate> candidates) throws IOException,InterruptedException {
        if (!configured() || candidates.isEmpty()) throw new IllegalStateException("报价 AI 未就绪");
        String prompt="根据匿名云仓报价，给运营选出最多三项值得进一步核价的方案。票价、月仓租、月增值费用单位不同，不可直接相加。缺失数字不能按零处理，优先选数据完整且价格有竞争力的方案。只返回JSON对象，shortlist为方案id的整数数组，checks从weights,materials,scope,volume,storage,extras,tax选择最多三项。";
        String data=json.writeValueAsString(candidates.stream().map(Candidate::numbers).toList());
        if(data.length()>60000) throw new IOException("候选方案超出单次评估容量");
        var body=Map.of("model",model,"messages",List.of(Map.of("role","system","content",prompt),Map.of("role","user","content",data)),
            "temperature",0.1,"max_tokens",768);
        var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(40))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200 || response.body().length()>100000) throw new IOException("报价评估服务不可用");
        String output=json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").trim();
        if (output.startsWith("```json") && output.endsWith("```")) output=output.substring(7,output.length()-3).trim();
        return suggestions(candidates,json.readTree(output));
    }
    static List<String> suggestions(List<Candidate> candidates,JsonNode answer) throws IOException {
        if (answer==null || !answer.path("shortlist").isArray() || !answer.path("checks").isArray()) throw new IOException("模型返回格式不正确");
        var result=new LinkedHashSet<String>();
        for (JsonNode item : answer.path("shortlist")) {
            if (!item.isIntegralNumber() || !item.canConvertToInt() || item.intValue()<1 || item.intValue()>candidates.size() || result.size()>=3) continue;
            var choice=candidates.get(item.intValue()-1);
            var n=choice.numbers();
            result.add("建议核价："+choice.label()+"，预估 "+n.get("perParcelMin")+"–"+n.get("perParcelMax")+" 元/票"
                +(Boolean.TRUE.equals(n.get("incomplete"))?"，部分费用待确认。":"。"));
        }
        if(result.isEmpty()) throw new IOException("模型未选出有效方案");
        var checks=new LinkedHashSet<String>();
        for (JsonNode item : answer.path("checks")) {
            var text=FOLLOW_UP.get(item.asText(""));
            if(text!=null && checks.size()<3) checks.add(text);
        }
        result.addAll(checks);
        return List.copyOf(result);
    }
}
