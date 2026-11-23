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
                "建议核价：测试仓，预估 2–3 元/票。","核实偏远附加费、适用范围和有效期");
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
        assertThat(candidates.get(0).numbers()).containsOnlyKeys("id","perParcelMin","perParcelMax","incomplete");
        var result=QuoteAdviceClient.suggestions(candidates,json.readTree("{\"shortlist\":[888,1],\"checks\":[\"materials\",\"自拟价格\"]}"));
        assertThat(result).containsExactly("建议核价：示例云仓 · 快递甲，预估 2–3 元/票。","核实仓供包材规格与单价");
        assertThatThrownBy(()->QuoteAdviceClient.suggestions(candidates,json.readTree("{\"shortlist\":[888],\"checks\":[]}")))
            .isInstanceOf(java.io.IOException.class);
    }
}
