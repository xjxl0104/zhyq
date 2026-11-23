package com.zhyq.park.marketing.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QuoteAdviceClientTest {
    final ObjectMapper json=new ObjectMapper();

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
