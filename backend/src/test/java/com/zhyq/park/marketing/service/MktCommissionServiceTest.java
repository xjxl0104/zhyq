package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.engine.LadderResolver;
import com.zhyq.park.marketing.engine.ChainNode;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktSettleBatch;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MktCommissionServiceTest {

    @Mock MktReferralOrderMapper orderMapper;
    @Mock MktPromoterCommissionMapper commissionMapper;
    @Mock MktSettleBatchMapper batchMapper;
    @Mock MktPromoterMapper promoterMapper;
    @Mock LadderResolver ladderResolver;
    @Mock MktAuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;

    MktCommissionService service;

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MktPromoterCommission.class);
        TableInfoHelper.initTableInfo(assistant, MktReferralOrder.class);
    }

    @BeforeEach
    void setUp() {
        service = new MktCommissionService(orderMapper, commissionMapper, batchMapper, promoterMapper,
                ladderResolver, auditService, eventPublisher);
    }

    @Test
    void createAndSplitWritesOrderAndOneRowPerPayee() {
        MktPromoter seller = promoter(1L, "P1");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(promoterMapper.selectById(1L)).thenReturn(seller);
        when(ladderResolver.chainOf(seller)).thenReturn(List.of(
                new ChainNode(1L, "P1", 1, false), new ChainNode(2L, "P2", 1, false),
                new ChainNode(3L, "P3", 1, false), new ChainNode(4L, "P4", 1, false)));
        when(ladderResolver.resolve(seller)).thenReturn(Map.of("P1", 50, "P2", 70, "P3", 85, "P4", 100));
        when(orderMapper.insert(any(MktReferralOrder.class))).thenAnswer(inv -> {
            ((MktReferralOrder) inv.getArgument(0)).setId(100L); return 1;
        });

        MktReferralOrder order = service.createAndSplit(leaseEvent("30000"));

        assertThat(order.getId()).isEqualTo(100L);
        assertThat(order.getStatus()).isEqualTo(MktCommissionService.ORDER_CONFIRMED);
        ArgumentCaptor<MktPromoterCommission> cap = ArgumentCaptor.forClass(MktPromoterCommission.class);
        verify(commissionMapper, times(4)).insert(cap.capture());
        List<MktPromoterCommission> rows = cap.getAllValues();
        assertThat(rows).extracting(MktPromoterCommission::getPromoterId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(rows).extracting(r -> r.getAmount().toPlainString())
                .containsExactly("15000.00", "6000.00", "4500.00", "4500.00");
        assertThat(rows).allMatch(r -> r.getStatus() == MktCommissionService.C_FROZEN);
        assertThat(rows).allMatch(r -> r.getReferralOrderId() == 100L);
        // 租赁:rate = 月数 × diff/100 → 成交人 1.00 × 50% = 0.5000
        assertThat(rows.get(0).getRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    void createAndSplitIsIdempotentOnSourceNo() {
        MktReferralOrder existing = new MktReferralOrder(); existing.setId(77L);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        MktReferralOrder order = service.createAndSplit(leaseEvent("30000"));

        assertThat(order.getId()).isEqualTo(77L);
        verify(orderMapper, never()).insert(any(MktReferralOrder.class));
        verify(commissionMapper, never()).insert(any(MktPromoterCommission.class));
    }

    @Test
    void unknownSellerIsRejected() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(promoterMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.createAndSplit(leaseEvent("1")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void unfreezeByOrderOnlyMovesFrozenRowsAndPublishesEvent() {
        MktPromoterCommission frozen = commission(11L, MktCommissionService.C_FROZEN, "100");
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(frozen));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int n = service.unfreezeByOrder(100L);

        assertThat(n).isEqualTo(1);
        verify(eventPublisher).publishEvent(any(DomainEvent.CommissionUnfrozen.class));
    }

    @Test
    void unfreezeDueSkipsRefundedOrders() {
        MktPromoterCommission due = commission(11L, MktCommissionService.C_FROZEN, "100");
        due.setReferralOrderId(100L);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(due));
        MktReferralOrder refunded = new MktReferralOrder(); refunded.setStatus(MktCommissionService.ORDER_REFUNDED);
        when(orderMapper.selectById(100L)).thenReturn(refunded);

        int n = service.unfreezeDue(LocalDateTime.now());

        assertThat(n).isZero();
        verify(commissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void clawbackVoidsUnsettledAndInsertsNegativeRowForSettled() {
        MktPromoterCommission frozen = commission(11L, MktCommissionService.C_FROZEN, "100");
        MktPromoterCommission settled = commission(12L, MktCommissionService.C_SETTLED, "60");
        frozen.setSign(1); settled.setSign(1);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(frozen, settled));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.clawback(100L, "退租");

        ArgumentCaptor<MktPromoterCommission> cap = ArgumentCaptor.forClass(MktPromoterCommission.class);
        verify(commissionMapper, times(1)).insert(cap.capture());
        MktPromoterCommission back = cap.getValue();
        assertThat(back.getSign()).isEqualTo(-1);
        assertThat(back.getAmount()).isEqualByComparingTo("-60");
        assertThat(back.getStatus()).isEqualTo(MktCommissionService.C_SETTLEABLE);
        verify(commissionMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void settleRejectsRowsNotInSettleableState() {
        when(batchMapper.insert(any(MktSettleBatch.class))).thenAnswer(inv -> {
            ((MktSettleBatch) inv.getArgument(0)).setId(9L); return 1;
        });
        when(commissionMapper.selectById(11L)).thenReturn(commission(11L, MktCommissionService.C_FROZEN, "100"));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.settle(List.of(11L), "ops"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是可结算状态");
    }

    @Test
    void settleSumsAmountsIntoBatch() {
        when(batchMapper.insert(any(MktSettleBatch.class))).thenAnswer(inv -> {
            ((MktSettleBatch) inv.getArgument(0)).setId(9L); return 1;
        });
        when(commissionMapper.selectById(11L)).thenReturn(commission(11L, MktCommissionService.C_SETTLEABLE, "100.50"));
        when(commissionMapper.selectById(12L)).thenReturn(commission(12L, MktCommissionService.C_SETTLEABLE, "-20"));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        String batchNo = service.settle(List.of(11L, 12L), "ops");

        assertThat(batchNo).startsWith("SB-");
        ArgumentCaptor<MktSettleBatch> cap = ArgumentCaptor.forClass(MktSettleBatch.class);
        verify(batchMapper).updateById(cap.capture());
        assertThat(cap.getValue().getCnt()).isEqualTo(2);
        assertThat(cap.getValue().getTotalAmount()).isEqualByComparingTo("80.50");
    }

    private static CommissionEvent leaseEvent(String pool) {
        return new CommissionEvent(MktCommissionService.SOURCE_LEASE, "LEASE-10", 10L, 5L, 1L, "A",
                new BigDecimal("1.00"), new BigDecimal(pool), new BigDecimal(pool),
                LocalDateTime.now(), null, 1L, null);
    }

    private static MktPromoter promoter(Long id, String code) {
        MktPromoter p = new MktPromoter(); p.setId(id); p.setPositionCode(code); p.setStatus(1); p.setIsInternal(0);
        return p;
    }

    private static MktPromoterCommission commission(Long id, int status, String amount) {
        MktPromoterCommission c = new MktPromoterCommission();
        c.setId(id); c.setStatus(status); c.setAmount(new BigDecimal(amount)); c.setPromoterId(1L);
        c.setPositionCode("P1"); c.setSharePct(50); c.setDiffPct(50); c.setRate(new BigDecimal("0.5"));
        c.setBaseAmount(BigDecimal.TEN); c.setReferralOrderId(100L);
        return c;
    }
}
