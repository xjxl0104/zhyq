package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktWarehouseSettlementLine;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.settlement.MktWarehouseSettlementService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WhBillingControllerTest {
    @BeforeAll static void tables(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),MktWarehouseSettlementLine.class);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void merchantReceivesOnlyItsCostCalculationFields() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wh:11",null,List.of()));
        var lines=mock(MktWarehouseSettlementLineMapper.class);var service=mock(MktWarehouseSettlementService.class);
        var line=new MktWarehouseSettlementLine();line.setSnapshotJson("{\"perOrder\":5,\"perItem\":0,\"qty\":100,\"packages\":100,\"commission\":60,\"serviceFee\":1000}");
        when(lines.selectList(any())).thenReturn(List.of(line));
        var controller=new WhBillingController(mock(MktServiceFeeBillMapper.class),mock(MktServiceFeeBillLineMapper.class),lines,mock(MktServiceFeeBillService.class),service,new ObjectMapper());
        var result=controller.settlementLines(8L).getData();
        verify(service).requireOwned(8L,11L);
        var safe=new ObjectMapper().readTree(result.get(0).getSnapshotJson());
        assertThat(safe.size()).isEqualTo(4);assertThat(safe.has("commission")).isFalse();assertThat(safe.has("serviceFee")).isFalse();assertThat(safe.get("packages").asInt()).isEqualTo(100);
    }
}
