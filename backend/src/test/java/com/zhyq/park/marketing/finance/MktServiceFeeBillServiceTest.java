package com.zhyq.park.marketing.finance;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktServiceFeeBillServiceTest {
    @Mock MktServiceFeeBillMapper bills; @Mock MktServiceFeeBillLineMapper lines;
    @Mock MktServiceContractMapper contracts; @Mock MktReferralOrderMapper orders;
    @Mock MktNoticeService notices; @Mock MktCommissionService commissions; @Mock MktPaymentProofService proofs; @Mock MktAuditService audit;
    @BeforeAll static void metadata(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");for(var c:List.of(MktServiceFeeBill.class,MktServiceFeeBillLine.class,MktServiceContract.class,MktReferralOrder.class))TableInfoHelper.initTableInfo(a,c);}
    MktServiceFeeBillService service(){return new MktServiceFeeBillService(bills,lines,contracts,orders,notices,commissions,proofs,audit);}
    MktServiceContract contract(){var c=new MktServiceContract();c.setId(9L);c.setWarehouseId(11L);c.setCustomerId(1L);c.setStatus(5);c.setSignMode(1);return c;}
    @Test void billSnapshotsOnlyExactContractAndCustomerOrders(){
        when(contracts.selectById(9L)).thenReturn(contract());
        var o=new MktReferralOrder();o.setId(21L);o.setServiceFee(new BigDecimal("12.50"));o.setSourceType(2);o.setSourceNo("OUT-1");
        when(orders.selectList(any())).thenAnswer(inv->{String sql=((com.baomidou.mybatisplus.core.conditions.Wrapper<?>)inv.getArgument(0)).getSqlSegment();assertThat(sql).contains("source_id","customer_id","warehouse_id");return List.of(o);});
        doAnswer(i->{((MktServiceFeeBill)i.getArgument(0)).setId(31L);return 1;}).when(bills).insert(any(MktServiceFeeBill.class));
        var b=service().generate(9L,LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));
        assertThat(b.getAmount()).isEqualByComparingTo("12.50");verify(lines).insert(any(MktServiceFeeBillLine.class));
    }
    @Test void overlappingPeriodCannotBeBilledAgain(){when(contracts.selectById(9L)).thenReturn(contract());when(bills.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(()->service().generate(9L,LocalDate.of(2026,9,1),LocalDate.of(2026,9,30))).isInstanceOf(BizException.class).hasMessageContaining("重叠");verify(bills,never()).insert(any(MktServiceFeeBill.class));}
    @Test void directSignOperatingShipmentsCannotBecomeParkReceivables() {
        var direct = contract(); direct.setSignMode(2);
        when(contracts.selectById(9L)).thenReturn(direct);
        assertThatThrownBy(() -> service().generate(9L,LocalDate.of(2026,9,1),LocalDate.of(2026,9,30)))
                .isInstanceOf(BizException.class).hasMessageContaining("仅用于园区签");
        verify(bills,never()).insert(any(MktServiceFeeBill.class));
        verifyNoInteractions(orders,lines,commissions,notices);
    }
    @Test void wrongWarehouseCannotConfirmBill(){var b=new MktServiceFeeBill();b.setWarehouseId(11L);when(bills.selectById(1L)).thenReturn(b);assertThatThrownBy(()->service().confirm(1L,22L)).isInstanceOf(BizException.class);verify(bills,never()).update(any(),any());}
    @Test void receiveRequiresExactAmountAndDoesNotUnfreezeOnMismatch(){var b=new MktServiceFeeBill();b.setId(1L);b.setWarehouseId(11L);b.setAmount(new BigDecimal("100"));b.setStatus(3);when(bills.selectById(1L)).thenReturn(b);
        assertThatThrownBy(()->service().receive(1L,"R1","file:5",new BigDecimal("99"))).isInstanceOf(BizException.class).hasMessageContaining("一致");verify(commissions,never()).unfreezeByOrderInTransaction(any());}
    @Test void receiptUnfreezesOnlyThisContractsBonusInSameTransaction(){var b=new MktServiceFeeBill();b.setId(1L);b.setWarehouseId(11L);b.setContractId(9L);b.setAmount(new BigDecimal("100"));b.setStatus(3);when(bills.selectById(1L)).thenReturn(b);when(bills.update(any(),any())).thenReturn(1);
        var bonus=new MktReferralOrder();bonus.setId(7L);when(orders.selectList(any())).thenReturn(List.of(bonus));service().receive(1L,"R1","file:5",new BigDecimal("100"));verify(proofs).require("file:5","mkt_bill",1L);verify(commissions).unfreezeByOrderInTransaction(7L);}
}
