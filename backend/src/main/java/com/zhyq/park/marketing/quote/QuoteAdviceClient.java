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

/** Sends anonymous operational choices and numeric offers. Returned choices must refer to actual offers. */
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
    record Candidate(String label, Map<String,Object> numbers, Map<String,Object> demand) {
        Candidate(String label,Map<String,Object> numbers){this(label,numbers,Map.of());}
    }
    List<Candidate> candidates(Map<String,Object> comparison) {
        var list=new ArrayList<Candidate>();
        // Reapply the allow-list at the provider boundary, including when comparing historical records.
        var profile=QuoteRequirements.assessmentProfile(json.valueToTree(comparison.get("demandProfile")));
        JsonNode warehouses=json.valueToTree(comparison.get("warehouses"));
        int warehouseIndex=0;
        for (JsonNode warehouse : warehouses) {
            warehouseIndex++;
            if (warehouse.has("version") && warehouse.path("version").asLong()==0) continue;
            for (JsonNode offer : warehouse.path("offers")) {
                if ("missing".equals(offer.path("status").asText())) continue;
                var numbers=new LinkedHashMap<String,Object>();
                numbers.put("id",list.size()+1);
                numbers.put("warehouse",warehouseIndex);
                for(String field:List.of("perParcelMin","perParcelMax","storageMonthlyMin","storageMonthlyMax")) {
                    if (offer.path(field).isNumber()) numbers.put(field,offer.path(field).decimalValue());
                }
                for(String part:List.of("handling","materials","freight")) {
                    for(String suffix:List.of("Min","Max")) {
                        String field=part+suffix;
                        JsonNode amount=offer.path("unitPrices").path(field);
                        if(amount.isNumber()) numbers.put(field,amount.decimalValue());
                    }
                }
                int availableServices=0;
                for(JsonNode service:offer.path("selectedServiceRates")) if(service.path("rate").isNumber()) availableServices++;
                if(availableServices>0) numbers.put("pricedServiceCount",availableServices);
                // Totals or actual operational unit rates qualify. Free customer-supplied
                // materials and the default zero extras alone provide no warehouse quote.
                if(numbers.keySet().stream().noneMatch(k->List.of("perParcelMin","freightMin","handlingMin","storageMonthlyMin","pricedServiceCount").contains(k))) continue;
                if(offer.path("extrasMonthly").isNumber()) numbers.put("extrasMonthly",offer.path("extrasMonthly").decimalValue());
                if(offer.path("dailyCapacity").isNumber()) numbers.put("dailyCapacity",offer.path("dailyCapacity").intValue());
                String capacity=offer.path("capacityFit").asText("");
                if(Set.of("fits","exceeded","uncertain","unknown").contains(capacity)) numbers.put("capacityFit",capacity);
                if(offer.path("regionalCoveragePartial").asBoolean(false)) numbers.put("limitedRegion",true);
                if(offer.path("missing").isArray()) numbers.put("missingCount",offer.path("missing").size());
                numbers.put("incomplete",!"estimate".equals(offer.path("status").asText()));
                list.add(new Candidate(warehouse.path("warehouseName").asText()+" · "+offer.path("carrier").asText(),numbers,profile));
            }
        }
        return list;
    }
    List<String> assess(List<Candidate> candidates) throws IOException,InterruptedException {
        if (!configured() || candidates.isEmpty()) throw new IllegalStateException("报价 AI 未就绪");
        String body = requestBody(candidates);
        var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(40))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200 || response.body().length()>100000) throw new IOException("报价评估服务不可用");
        return readResponse(candidates,response.body());
    }

    String requestBody(List<Candidate> candidates) throws IOException {
        String prompt="根据每个仓为本客户匹配的匿名报价、费用完整度和产能匹配结果，给运营选择首选分配方案与备选，按推荐顺序最多三项。优先满足客户产能，capacityFit=exceeded表示登记产能不足。只有分项的方案仅可建议先补报价。对比同口径票价、仓租、已报价增值项和缺项数。票价、月仓租、月增值费用单位不同，不可直接相加，缺价不得按零估算。只返回 JSON 对象，shortlist 为按推荐排序的方案 id 整数数组，checks 从 weights,materials,scope,volume,storage,extras,tax 选最多三项。";
        var offers=candidates.stream().map(Candidate::numbers).toList();
        var profile=candidates.get(0).demand();
        String data=json.writeValueAsString(profile.isEmpty()?offers:Map.of("demand",profile,"offers",offers));
        if(data.length()>60000) throw new IOException("候选方案超出单次评估容量");
        var body=json.createObjectNode();
        body.put("model",model);
        var messages=body.putArray("messages");
        messages.addObject().put("role","system").put("content",prompt);
        messages.addObject().put("role","user").put("content",data);
        body.put("temperature",0.1);
        // The gateway counts DeepSeek reasoning and the final answer against this shared limit.
        body.put("max_tokens","deepseek-flash".equals(model) ? 4096 : 768);
        return json.writeValueAsString(body);
    }

    List<String> readResponse(List<Candidate> candidates,String responseBody) throws IOException {
        JsonNode response=json.readTree(responseBody);
        if (response==null) throw new IOException("报价评估服务返回了空响应");
        var choice=response.path("choices").path(0);
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new IOException("报价评估达到输出上限，未取得完整建议");
        }
        String output=choice.path("message").path("content").asText("").trim();
        if (output.isEmpty()) throw new IOException("报价评估服务未返回建议内容");
        if (output.startsWith("```json") && output.endsWith("```")) output=output.substring(7,output.length()-3).trim();
        return suggestions(candidates,json.readTree(output));
    }
    static List<String> suggestions(List<Candidate> candidates,JsonNode answer) throws IOException {
        if (answer==null || !answer.path("shortlist").isArray() || !answer.path("checks").isArray()) throw new IOException("模型返回格式不正确");
        var result=new LinkedHashSet<String>();
        var chosenIds=new HashSet<Integer>();
        var chosenWarehouses=new HashSet<Object>();
        var warehouseCount=candidates.stream().map(c->c.numbers().getOrDefault("warehouse",c.label())).distinct().count();
        for (JsonNode item : answer.path("shortlist")) {
            if (!item.isIntegralNumber() || !item.canConvertToInt() || item.intValue()<1 || item.intValue()>candidates.size() || result.size()>=3) continue;
            if(!chosenIds.add(item.intValue())) continue;
            var choice=candidates.get(item.intValue()-1);
            var n=choice.numbers();
            Object warehouse=n.getOrDefault("warehouse",choice.label());
            boolean sameWarehouse=chosenWarehouses.contains(warehouse);
            var facts=new ArrayList<String>();
            boolean fullTicket=n.containsKey("perParcelMax");
            boolean insufficient="exceeded".equals(n.get("capacityFit"));
            boolean limitedRegion=Boolean.TRUE.equals(n.get("limitedRegion"));
            String heading;
            if(insufficient) heading="产能不足，需另核";
            else if(!fullTicket || limitedRegion) heading=sameWarehouse?"同仓待补价方案":"待补价候选";
            else if(sameWarehouse) heading="同仓承运备选";
            else if(warehouseCount==1) heading="唯一报价仓的可选方案";
            else heading=result.isEmpty()?"建议优先分配":"备选云仓";
            if(fullTicket) facts.add("预估票价 "+range(n,"perParcel")+" 元/票");
            else {
                if(n.containsKey("freightMax")) facts.add("运费 "+range(n,"freight")+" 元/票");
                if(n.containsKey("handlingMax")) facts.add("操作费 "+range(n,"handling")+" 元/票");
                facts.add("单票总价待补齐");
            }
            if(n.containsKey("storageMonthlyMax")) facts.add("30 天仓租 "+range(n,"storageMonthly")+" 元");
            if(n.containsKey("pricedServiceCount")) facts.add("已报 "+n.get("pricedServiceCount")+" 项增值服务");
            if("fits".equals(n.get("capacityFit"))) facts.add("登记产能覆盖需求上限");
            if("uncertain".equals(n.get("capacityFit"))) facts.add("高峰产能需确认");
            if(limitedRegion) facts.add("报价仅覆盖部分地区或单量档");
            if(Boolean.TRUE.equals(n.get("incomplete"))) facts.add("部分费用待确认");
            result.add(heading+"："+choice.label()+"，"+String.join("，",facts)+"。");
            chosenWarehouses.add(warehouse);
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

    private static String range(Map<String,Object> values,String field) {
        Object low=values.get(field+"Min"),high=values.get(field+"Max");
        return Objects.equals(low,high)?String.valueOf(low):low+"–"+high;
    }
}
