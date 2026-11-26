package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QuoteAdviceClientTest {
    final ObjectMapper json=new ObjectMapper();
    private final List<QuoteAdviceClient.Candidate> candidates=List.of(
            new QuoteAdviceClient.Candidate("测试仓",Map.of("id",1,"perParcelMin",2,"perParcelMax",3)));

    @Test void flashLeavesRoomForReasoningAndTheFinalAnswerWithoutNativeGatewayOptions() throws Exception {
        var client=new QuoteAdviceClient(json,"","","deepseek-flash",false);
        var request=json.readTree(client.requestBody(candidates));
        assertThat(request.has("thinking")).isFalse();
        assertThat(request.path("max_tokens").asInt()).isEqualTo(4096);
        var input=json.readTree(request.path("messages").get(1).path("content").asText());
        assertThat(input).isEqualTo(json.valueToTree(candidates.stream().map(QuoteAdviceClient.Candidate::numbers).toList()));
    }

    @Test void unrelatedModelDoesNotReceiveDeepSeekSpecificOptions() throws Exception {
        var client=new QuoteAdviceClient(json,"","","another-model",false);
        var request=json.readTree(client.requestBody(candidates));
        assertThat(request.has("thinking")).isFalse();
        assertThat(request.path("model").asText()).isEqualTo("another-model");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(768);
    }

    @Test void truncatedAnswersAreRejectedEvenWhenTheirContentLooksComplete() throws Exception {
        var client=new QuoteAdviceClient(json,"","","deepseek-flash",false);
        String valid="{\"shortlist\":[1],\"checks\":[\"scope\"]}";
        for (String content : List.of("",valid)) {
            String response=response("length",content);
            assertThatThrownBy(()->client.readResponse(candidates,response)).isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("输出上限");
        }
        assertThat(client.readResponse(candidates,response("stop",valid))).containsExactly(
                "唯一报价仓的可选方案：测试仓，预估票价 2–3 元/票。","核实偏远附加费、适用范围和有效期");
        assertThatThrownBy(()->client.readResponse(candidates,response("stop",""))).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("未返回建议内容");
    }

    private String response(String reason,String content) throws Exception {
        return json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",reason,
                "message",Map.of("reasoning_content","模型内部推理","content",content)))));
    }

    @Test void externalRequestsRequireVendorHttpsAndConfirmedBudget() {
        assertThat(new QuoteAdviceClient(json,"https://token.vvaix.com/v1/chat/completions","key","model",false).configured()).isFalse();
        assertThat(new QuoteAdviceClient(json,"https://vvaix.com.example.org/v1/chat","key","model",true).configured()).isFalse();
        assertThat(new QuoteAdviceClient(json,"https://token.vvaix.com/v1/chat/completions","key","model",true).configured()).isTrue();
    }

    @Test void providerSeesOnlyAnonymousNumbersAndCannotInventPrices() throws Exception {
        var client=new QuoteAdviceClient(json,"","","",false);
        var comparison=Map.<String,Object>of("customerId",303,"warehouses",List.of(Map.of("warehouseName","示例云仓","offers",List.of(
            Map.of("carrier","快递甲","status","estimate","scopeNote","私有备注","perParcelMin",2,"perParcelMax",3),
            Map.of("carrier","缺价快递","status","partial")))));
        var candidates=client.candidates(comparison);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).numbers()).containsOnlyKeys("id","warehouse","perParcelMin","perParcelMax","incomplete");
        var result=QuoteAdviceClient.suggestions(candidates,json.readTree("{\"shortlist\":[888,1],\"checks\":[\"materials\",\"自拟价格\"]}"));
        assertThat(result).containsExactly("唯一报价仓的可选方案：示例云仓 · 快递甲，预估票价 2–3 元/票。","核实仓供包材规格与单价");
        assertThatThrownBy(()->QuoteAdviceClient.suggestions(candidates,json.readTree("{\"shortlist\":[888],\"checks\":[]}")))
            .isInstanceOf(java.io.IOException.class);
    }
    @Test void incompleteFreightCanBeComparedWithoutRecommendingAMissingOrUnpricedWarehouse() throws Exception {
        var client=new QuoteAdviceClient(json,"","","",false);
        var partial=Map.of("carrier","圆通","status","partial","unitPrices",Map.of("freightMin",2,"freightMax",4),"capacityFit","uncertain");
        var comparison=Map.<String,Object>of("warehouses",List.of(
            Map.of("warehouseName","无价仓","version",0,"offers",List.of(partial)),
            Map.of("warehouseName","部分报价仓","version",1,"offers",List.of(partial,Map.of("carrier","不可用","status","missing","storageMonthlyMin",0)))));
        var offers=client.candidates(comparison);
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).numbers()).doesNotContainKeys("perParcelMin","perParcelMax");
        assertThat(QuoteAdviceClient.suggestions(offers,json.readTree("{\"shortlist\":[1],\"checks\":[]}")).get(0))
            .contains("待补价候选","运费 2–4 元/票","单票总价待补齐","高峰产能需确认");
    }

    @Test void sendsNormalizedCustomerNeedsOnceWithoutPrivateText() throws Exception {
        var client=new QuoteAdviceClient(json,"","","",false);
        var comparison=Map.<String,Object>of("demandProfile",Map.of("dailyOrders","0-200","palletCount",7,
            "productCategories",List.of("日化清洁","其他","客户私有字样"),"productCategoryOther","私人品牌","packingNote","私人备注"),
            "warehouses",List.of(Map.of("warehouseName","内部仓名","version",2,"offers",List.of(
                Map.of("carrier","圆通","status","estimate","perParcelMin",3,"perParcelMax",3)))));
        var request=json.readTree(client.requestBody(client.candidates(comparison)));
        var user=request.path("messages").get(1).path("content").asText();
        assertThat(user).contains("日化清洁","0-200","palletCount").doesNotContain("私人","客户私有","内部仓名");
    }

    @Test void twoCarriersForOneWarehouseDoNotAppearAsTwoCompetingWarehouses() throws Exception {
        var options=List.of(new QuoteAdviceClient.Candidate("甲仓 · 圆通",Map.of("id",1,"warehouse",7,"perParcelMin",2,"perParcelMax",3)),
                new QuoteAdviceClient.Candidate("甲仓 · 顺丰",Map.of("id",2,"warehouse",7,"perParcelMin",5,"perParcelMax",7)));
        var lines=QuoteAdviceClient.suggestions(options,json.readTree("{\"shortlist\":[1,2],\"checks\":[]}"));
        assertThat(lines.get(0)).contains("唯一报价仓");
        assertThat(lines.get(1)).contains("同仓承运备选").doesNotContain("备选云仓");
    }
}
