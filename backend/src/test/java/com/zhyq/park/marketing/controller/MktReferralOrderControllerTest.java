package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MktReferralOrderControllerTest {
    @BeforeAll static void tables(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,MktReferralOrder.class);TableInfoHelper.initTableInfo(a,MktServiceFeeBillLine.class);}
    @Test void billedOrderMustUseFinancialCorrectionInsteadOfDirectVoid(){
        var orders=mock(MktReferralOrderMapper.class);var lines=mock(MktServiceFeeBillLineMapper.class);var commissions=mock(MktCommissionService.class);
        when(orders.update(isNull(),any())).thenReturn(1);when(lines.selectCount(any())).thenReturn(1L);
        var controller=new MktReferralOrderController(orders,mock(MktPromoterCommissionMapper.class),mock(MktPromoterMapper.class),mock(CustomerMapper.class),mock(MktReferralOrderImportService.class),commissions,mock(MktAuditService.class),lines);
        assertThatThrownBy(()->controller.voidOrder(7L,Map.of("reason","退款"))).isInstanceOf(BizException.class).hasMessageContaining("联系财务");
        verifyNoInteractions(commissions);
        var inOrder=inOrder(orders,lines);inOrder.verify(orders).update(isNull(),any());inOrder.verify(lines).selectCount(any());
    }
}
