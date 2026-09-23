package com.zhyq.park.marketing.settlement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktWarehouseSettlementServiceTest {
    @Mock MktWarehouseSettlementMapper settlements;
    @Mock MktWarehouseSettlementLineMapper lines;
    @Mock MktServiceFeeBillMapper bills;
    @Mock MktDirectSignPaymentMapper payments;
    @Mock MktReferralOrderMapper orders;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktErpReconcileSnapshotMapper reconcile;
    @Mock MktCommissionService commissions;
    @Mock MktNoticeService notices;
    @Mock MktPromoterCommissionMapper commissionRows;
    @Mock MktServiceFeeBillLineMapper billLines;
    @Mock MktServiceContractMapper contracts;
    @Mock MktCustomerGradeMapper grades;
    @Mock MktPaymentProofService proofs;
    @Mock MktAuditService audit;
    static final LocalDate START=LocalDate.of(2026,9,1), END=LocalDate.of(2026,9,30);

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for(Class<?> type : List.of(MktWarehouseSettlement.class,MktWarehouseSettlementLine.class,
                MktServiceFeeBill.class,MktServiceFeeBillLine.class,MktDirectSignPayment.class,
                MktReferralOrder.class,MktWarehouse.class,MktPromoterCommission.class,
                MktErpReconcileSnapshot.class,MktCustomerGrade.class,MktServiceContract.class)) TableInfoHelper.initTableInfo(a,type);
    }
    private MktWarehouseSettlementService service() {
        return new MktWarehouseSettlementService(settlements,lines,bills,payments,orders,warehouses,
                reconcile,commissions,notices,commissionRows,billLines,contracts,grades,proofs,audit,new ObjectMapper());
    }
    @Test void computesPayableFromCostRatesNotCustomerRevenue() {
        var w=new MktWarehouse(); w.setId(11L); w.setJoinStatus(5); w.setOrderMode("manual");
        w.setFeeModel("{\"perOrder\":2,\"perItem\":0.5}"); when(warehouses.selectById(11L)).thenReturn(w);
        var bill=new MktServiceFeeBill();bill.setId(20L);bill.setAmount(new BigDecimal("100.00"));
        when(bills.selectList(any())).thenReturn(List.of(bill));
        var line=new MktServiceFeeBillLine();line.setReferralOrderId(30L);
        when(billLines.selectList(any())).thenReturn(List.of(line));
        var order=new MktReferralOrder();order.setWarehouseId(11L);order.setStatus(2);order.setQty(6);order.setPackages(2);order.setPoolAmount(BigDecimal.ONE);
        when(orders.selectById(30L)).thenReturn(order);
        doAnswer(i->{i.getArgument(0,MktWarehouseSettlement.class).setId(40L);return 1;}).when(settlements).insert(any(MktWarehouseSettlement.class));
        var result=service().generate(11L,START,END);
        assertThat(result.getAmount()).isEqualByComparingTo("7.00");assertThat(result.getStatus()).isEqualTo(2);
        ArgumentCaptor<MktWarehouseSettlementLine> captured=ArgumentCaptor.forClass(MktWarehouseSettlementLine.class);
        verify(lines).insert(captured.capture());assertThat(captured.getValue().getSnapshotJson()).contains("perOrder","100.00");
    }
    @Test void cannotConfirmWhileRealReconciliationRemainsFrozen() {
        var s=settlement(2);when(settlements.selectById(40L)).thenReturn(s);
        var snap=new MktErpReconcileSnapshot();snap.setFrozen(1);when(reconcile.selectOne(any())).thenReturn(snap);
        assertThatThrownBy(()->service().confirm(40L,11L)).isInstanceOf(BizException.class).hasMessageContaining("冻结");
        verify(settlements,never()).update(any(),any());
    }
    @Test void anotherWarehouseCannotConfirm() {
        when(settlements.selectById(40L)).thenReturn(settlement(2));
        assertThatThrownBy(()->service().confirm(40L,12L)).isInstanceOf(BizException.class).hasMessageContaining("不属于");
        verifyNoInteractions(reconcile);
    }
    @Test void paymentRequiresConfirmedExactAmountAndProof() {
        var s=settlement(3);when(settlements.selectById(40L)).thenReturn(s);
        assertThatThrownBy(()->service().pay(40L,"P1","file:1",new BigDecimal("9.00"),"admin"))
                .isInstanceOf(BizException.class).hasMessageContaining("实付金额");
        verifyNoInteractions(proofs);
        when(settlements.update(isNull(),any())).thenReturn(1);
        service().pay(40L,"P1","file:1",new BigDecimal("10.00"),"admin");
        verify(proofs).require("file:1","mkt_settlement",40L);
    }
    @Test void platformCommissionUsesOnlyActualReceivedPlatformFee() {
        when(contracts.selectById(9L)).thenReturn(contract());
        var grade=new MktCustomerGrade();grade.setErpTotalRate(new BigDecimal("5"));when(grades.selectOne(any())).thenReturn(grade);
        var order=new MktReferralOrder();order.setId(31L);when(commissions.createAndSplitInTransaction(any())).thenReturn(order);
        var bonus=new MktReferralOrder();bonus.setId(32L);when(orders.selectList(any())).thenReturn(List.of(bonus));
        var result=service().recordPayment(9L,11L,"PAY1",new BigDecimal("80.00"),"file:1");
        assertThat(result.getAmount()).isEqualByComparingTo("80.00");
        ArgumentCaptor<MktCommissionService.CommissionEvent> event=ArgumentCaptor.forClass(MktCommissionService.CommissionEvent.class);
        verify(commissions).createAndSplitInTransaction(event.capture());
        assertThat(event.getValue().baseAmount()).isEqualByComparingTo("80.00");
        assertThat(event.getValue().poolAmount()).isEqualByComparingTo("4.00");
        assertThat(event.getValue().sourceType()).isEqualTo(MktCommissionService.SOURCE_PLATFORM_FEE);
        verify(commissions).unfreezeByOrderInTransaction(31L);
        verify(commissions).unfreezeByOrderInTransaction(32L);
        verify(contracts).update(isNull(),any());
        verify(proofs).require("file:1","mkt_direct_payment",9L);
    }
    @Test void reusedPlatformPaymentNumberCannotCrossContract() {
        when(contracts.selectById(9L)).thenReturn(contract());
        var old=new MktDirectSignPayment();old.setContractId(10L);when(payments.selectOne(any())).thenReturn(old);
        assertThatThrownBy(()->service().recordPayment(9L,11L,"PAY1",BigDecimal.TEN,"file:1"))
                .isInstanceOf(BizException.class).hasMessageContaining("其他到账");
        verifyNoInteractions(commissions,proofs);
    }
    @Test void exactRepeatedPlatformReceiptDoesNotCreateCommissionTwice() {
        when(contracts.selectById(9L)).thenReturn(contract());
        var old=new MktDirectSignPayment();old.setContractId(9L);old.setWarehouseId(11L);old.setAmount(BigDecimal.TEN);old.setPayProof("file:1");
        when(payments.selectOne(any())).thenReturn(old);
        assertThat(service().recordPayment(9L,11L,"PAY1",BigDecimal.TEN,"file:1")).isSameAs(old);
        verifyNoInteractions(commissions,proofs);
    }
    private MktServiceContract contract(){var c=new MktServiceContract();c.setId(9L);c.setWarehouseId(11L);c.setSignMode(2);c.setStatus(4);c.setPartnerId(8L);c.setCustomerId(5L);c.setGrade("A");return c;}
    private MktWarehouseSettlement settlement(int status){var s=new MktWarehouseSettlement();s.setId(40L);s.setWarehouseId(11L);s.setStatus(status);s.setAmount(BigDecimal.TEN);s.setPeriodStart(START);s.setPeriodEnd(END);return s;}
}
