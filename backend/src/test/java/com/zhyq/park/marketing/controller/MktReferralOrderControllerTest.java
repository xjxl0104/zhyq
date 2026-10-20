package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MktReferralOrderControllerTest {
    @BeforeAll static void tables(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,MktReferralOrder.class);TableInfoHelper.initTableInfo(a,MktServiceFeeBillLine.class);TableInfoHelper.initTableInfo(a,MktServiceContract.class);}
    @Test void everyServiceContractSourceShowsItsContractNumber(){
        var orders=mock(MktReferralOrderMapper.class);var contracts=mock(MktServiceContractMapper.class);
        var sc=new MktServiceContract();sc.setId(9L);sc.setContractNo("CS-9");sc.setSignMode(1);when(contracts.selectById(9L)).thenReturn(sc);
        var controller=controller(orders,contracts);
        for(int type:new int[]{MktCommissionService.SOURCE_OUTBOUND,MktCommissionService.SOURCE_PLATFORM_FEE,MktCommissionService.SOURCE_CONTRACT_BONUS}){
            var o=new MktReferralOrder();o.setId(1L);o.setSourceType(type);o.setSourceId(9L);when(orders.selectById(1L)).thenReturn(o);
            assertThat(controller.get(1L).getData()).containsEntry("contractNo","CS-9");
        }
        // 租赁签约的 sourceId 指向 biz_contract,不能拿去查服务合同
        var lease=new MktReferralOrder();lease.setId(2L);lease.setSourceType(MktCommissionService.SOURCE_LEASE);lease.setSourceId(9L);when(orders.selectById(2L)).thenReturn(lease);
        assertThat(controller.get(2L).getData()).doesNotContainKey("contractNo");
    }
    @SuppressWarnings("unchecked")
    @Test void contractSummaryAmountsOnlyCountConfirmedOrders(){
        var orders=mock(MktReferralOrderMapper.class);var contracts=mock(MktServiceContractMapper.class);
        var sc=new MktServiceContract();sc.setId(5L);sc.setContractNo("CS-5");
        when(contracts.selectPage(any(),any())).thenAnswer(inv->{Page<MktServiceContract> p=inv.getArgument(0);p.setRecords(List.of(sc));p.setTotal(1);return p;});
        when(orders.selectMaps(any())).thenReturn(List.of(Map.of("source_id",5L,"orderCount",3L,"confirmedCount",new BigDecimal("1"),"serviceFeeSum",new BigDecimal("10.00"),"poolSum",new BigDecimal("2.00"))));
        var row=controller(orders,contracts).contractSummary(1,10,null,null,null,null,null).getData().getRecords().get(0);
        assertThat(row).containsEntry("serviceFeeSum",new BigDecimal("10.00")).containsEntry("poolSum",new BigDecimal("2.00"));
        // 与服务费账单、营销看板同口径:作废/退款订单的金额不能计入合计
        ArgumentCaptor<QueryWrapper<MktReferralOrder>> sql=ArgumentCaptor.forClass(QueryWrapper.class);verify(orders).selectMaps(sql.capture());
        assertThat(sql.getValue().getSqlSelect()).contains("SUM(CASE WHEN status = 2 THEN service_fee ELSE 0 END)").contains("SUM(CASE WHEN status = 2 THEN pool_amount ELSE 0 END)");
    }
    private static MktReferralOrderController controller(MktReferralOrderMapper orders,MktServiceContractMapper contracts){
        return controller(orders,contracts,mock(MktServiceFeeBillLineMapper.class));
    }
    private static MktReferralOrderController controller(MktReferralOrderMapper orders,MktServiceContractMapper contracts,MktServiceFeeBillLineMapper lines){
        return new MktReferralOrderController(orders,mock(MktPromoterCommissionMapper.class),mock(MktPromoterMapper.class),mock(CustomerMapper.class),contracts,mock(MktWarehouseMapper.class),mock(MktReferralOrderImportService.class),mock(MktCommissionService.class),mock(MktAuditService.class),lines,mock(MktCustomerPricingService.class),mock(MktAdminActorService.class));
    }
    @Test void billedOrderMustUseFinancialCorrectionInsteadOfDirectVoid(){
        var orders=mock(MktReferralOrderMapper.class);var lines=mock(MktServiceFeeBillLineMapper.class);var commissions=mock(MktCommissionService.class);
        when(orders.update(isNull(),any())).thenReturn(1);when(lines.selectCount(any())).thenReturn(1L);
        var controller=new MktReferralOrderController(orders,mock(MktPromoterCommissionMapper.class),mock(MktPromoterMapper.class),mock(CustomerMapper.class),mock(MktServiceContractMapper.class),mock(MktWarehouseMapper.class),mock(MktReferralOrderImportService.class),commissions,mock(MktAuditService.class),lines,mock(MktCustomerPricingService.class),mock(MktAdminActorService.class));
        assertThatThrownBy(()->controller.voidOrder(7L,Map.of("reason","退款"))).isInstanceOf(BizException.class).hasMessageContaining("联系财务");
        verifyNoInteractions(commissions);
        var inOrder=inOrder(orders,lines);inOrder.verify(orders).update(isNull(),any());inOrder.verify(lines).selectCount(any());
    }
}
