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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.lang.reflect.Method;

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
    @Mock com.zhyq.park.marketing.mapper.MktWithdrawalMapper withdrawalMapper;

    MktCommissionService service;

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MktPromoterCommission.class);
        TableInfoHelper.initTableInfo(assistant, MktReferralOrder.class);
        TableInfoHelper.initTableInfo(assistant, com.zhyq.park.marketing.entity.MktWithdrawal.class);
    }

    @BeforeEach
    void setUp() {
        service = new MktCommissionService(orderMapper, commissionMapper, batchMapper, promoterMapper,
                ladderResolver, auditService, eventPublisher, withdrawalMapper);
    }

    @Test
    void createAndSplitWritesOrderAndOneRowPerPayee() {
        MktPromoter seller = promoter(1L, "P1");
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(promoterMapper.selectForUpdate(1L)).thenReturn(seller);
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
        when(promoterMapper.selectForUpdate(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.createAndSplit(leaseEvent("1")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void afterCommitEntryPointsRequireNewTransaction() throws Exception {
        Method create = MktCommissionService.class.getMethod("createAndSplit", CommissionEvent.class);
        Method unfreeze = MktCommissionService.class.getMethod("unfreezeByOrder", Long.class);
        assertThat(create.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(unfreeze.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
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
    void refreezeByOrderOnlyMovesSettleablePositiveRows() {
        MktPromoterCommission settleable = commission(11L, MktCommissionService.C_SETTLEABLE, "100");
        settleable.setSign(1);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(settleable));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThat(service.refreezeByOrder(100L)).isEqualTo(1);
        verify(commissionMapper).update(isNull(), any(Wrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
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
    void clawbackRechecksWithdrawalAfterTakingPromoterLock() {
        var stale = commission(11L, MktCommissionService.C_SETTLED, "100"); stale.setPromoterId(7L);
        var locked = commission(11L, MktCommissionService.C_SETTLED, "100"); locked.setPromoterId(7L); locked.setWithdrawalId(99L);
        when(commissionMapper.selectList(any())).thenReturn(List.of(stale), List.of(locked));
        when(withdrawalMapper.update(isNull(), any())).thenReturn(1);
        when(commissionMapper.update(isNull(), any())).thenReturn(1);
        service.clawback(100L, "退款");
        verify(withdrawalMapper).update(isNull(), any());
        var order = org.mockito.Mockito.inOrder(commissionMapper, promoterMapper);
        order.verify(commissionMapper).selectList(any()); order.verify(promoterMapper).selectForUpdate(7L); order.verify(commissionMapper).selectList(any());
        verify(commissionMapper).insert(any(MktPromoterCommission.class));
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
        verify(commissionMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void settleRejectsRowsNotInSettleableState() {
        when(batchMapper.insert(any(MktSettleBatch.class))).thenAnswer(inv -> {
            ((MktSettleBatch) inv.getArgument(0)).setId(9L); return 1;
        });
        MktPromoterCommission frozen = commission(11L, MktCommissionService.C_FROZEN, "100");
        frozen.setSign(1);
        when(commissionMapper.selectById(11L)).thenReturn(frozen);
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.settle(List.of(11L), "ops"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是可结算状态");
    }

    @Test
    void settleRejectsClawbackRows() {
        when(batchMapper.insert(any(MktSettleBatch.class))).thenReturn(1);
        MktPromoterCommission clawback = commission(11L, MktCommissionService.C_SETTLEABLE, "-20");
        clawback.setSign(-1);
        when(commissionMapper.selectById(11L)).thenReturn(clawback);
        assertThatThrownBy(() -> service.settle(List.of(11L), "ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("扣回");
        verify(commissionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void settleSumsAmountsIntoBatch() {
        when(batchMapper.insert(any(MktSettleBatch.class))).thenAnswer(inv -> {
            ((MktSettleBatch) inv.getArgument(0)).setId(9L); return 1;
        });
        MktPromoterCommission positive = commission(11L, MktCommissionService.C_SETTLEABLE, "100.50"); positive.setSign(1);
        MktPromoterCommission negative = commission(12L, MktCommissionService.C_SETTLEABLE, "-20"); negative.setSign(-1);
        when(commissionMapper.selectById(11L)).thenReturn(positive);
        when(commissionMapper.selectById(12L)).thenReturn(negative);
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThatThrownBy(() -> service.settle(List.of(11L, 12L), "ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("扣回");
    }

    @Test void reversalOffsetsSettledAndWithdrawnWithoutRewritingPositiveHistory() {
        var settled = commission(11L, MktCommissionService.C_SETTLED, "100"); settled.setSign(1);
        var paid = commission(12L, MktCommissionService.C_WITHDRAWN, "60"); paid.setSign(1); paid.setWithdrawalId(8L);
        when(commissionMapper.selectList(any())).thenReturn(List.of(settled, paid));
        when(commissionMapper.update(isNull(), any())).thenReturn(1);
        service.refreezeByOrder(100L);
        var cap = ArgumentCaptor.forClass(MktPromoterCommission.class);
        verify(commissionMapper, times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(MktPromoterCommission::getAmount)
                .containsExactly(new BigDecimal("-100"), new BigDecimal("-60"));
        verify(commissionMapper, times(2)).update(isNull(), org.mockito.ArgumentMatchers.argThat(q -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) q;
            return !update.getSqlSet().contains("amount") && !update.getSqlSet().contains("status");
        }));
        verify(withdrawalMapper, never()).update(isNull(), any());
    }

    @Test void repeatedClawbackUsesTheExistingUniqueDebit() {
        var settled = commission(11L, MktCommissionService.C_SETTLED, "100"); settled.setSign(1);
        when(commissionMapper.selectList(any())).thenReturn(List.of(settled));
        when(commissionMapper.insert(any(MktPromoterCommission.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("existing debit"));
        when(commissionMapper.update(isNull(), any())).thenReturn(1);
        service.clawback(100L, "退款");
        assertThat(settled.getReceiptSuspended()).isEqualTo(2);
    }

    @Test void outboundReplayCannotChangeCustomerOrWarehouse() {
        var old = new MktReferralOrder(); old.setId(9L); old.setWarehouseId(2L); old.setCustomerId(5L); old.setSourceId(10L);
        when(orderMapper.selectOne(any())).thenReturn(old);
        var ev = new CommissionEvent(2, "OUT-1", 10L, 5L, 1L, "A", BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE, LocalDateTime.now(), null, 1L, 1L);
        assertThatThrownBy(() -> service.createAndSplit(ev)).isInstanceOf(BizException.class).hasMessageContaining("禁止覆盖");
        verify(orderMapper, never()).insert(any(MktReferralOrder.class));
    }

    @Test void twoReversalAndReceiptCyclesPreservePaidHistoryAndRestoreExactEntitlement() {
        var original = commission(11L, MktCommissionService.C_WITHDRAWN, "100"); original.setWithdrawalId(88L);
        var ledger = new java.util.ArrayList<MktPromoterCommission>(); ledger.add(original);
        when(commissionMapper.selectList(any())).thenAnswer(i -> new java.util.ArrayList<>(ledger));
        when(commissionMapper.update(isNull(),any())).thenReturn(1);
        when(commissionMapper.insert(any(MktPromoterCommission.class))).thenAnswer(i -> {
            var row = i.getArgument(0,MktPromoterCommission.class); row.setId(100L + ledger.size()); ledger.add(row); return 1;
        });
        for (int cycle = 1; cycle <= 2; cycle++) {
            assertThat(service.refreezeByOrder(100L)).isEqualTo(1);
            assertThat(service.refreezeByOrder(100L)).isZero(); // duplicate red reversal
            var debit = ledger.get(ledger.size()-1);
            assertThat(debit.getAmount()).isEqualByComparingTo("-100");
            assertThat(debit.getAdjustmentSequence()).isEqualTo(cycle);
            assertThat(debit.getPromoterId()).isEqualTo(original.getPromoterId());
            assertThat(debit.getReferralOrderId()).isEqualTo(original.getReferralOrderId());
            // The first debit has already been deducted in a paid withdrawal; restoration must still work.
            if (cycle == 1) { debit.setStatus(MktCommissionService.C_WITHDRAWN); debit.setWithdrawalId(89L); }
            else debit.setStatus(MktCommissionService.C_SETTLED); // legacy settled debit remains in the ledger
            assertThat(service.unfreezeByOrderInTransaction(100L)).isEqualTo(1);
            assertThat(service.unfreezeByOrderInTransaction(100L)).isZero(); // duplicate collection callback
            var credit = ledger.get(ledger.size()-1);
            assertThat(credit.getAmount()).isEqualByComparingTo("100");
            assertThat(credit.getStatus()).isEqualTo(MktCommissionService.C_SETTLED);
            assertThat(credit.getAdjustmentSequence()).isEqualTo(cycle);
            assertThat(ledger.stream().map(MktPromoterCommission::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
        }
        assertThat(ledger).hasSize(5);
        assertThat(original.getStatus()).isEqualTo(MktCommissionService.C_WITHDRAWN);
        assertThat(original.getWithdrawalId()).isEqualTo(88L);
        assertThat(original.getAmount()).isEqualByComparingTo("100");
        assertThat(original.getReceiptRevision()).isEqualTo(2);
    }

    @Test void permanentTerminationWhileSuspendedDoesNotDeductTwiceOrRestoreOnLatePayment() {
        var original = commission(11L,MktCommissionService.C_SETTLED,"100");
        var ledger = new java.util.ArrayList<MktPromoterCommission>(); ledger.add(original);
        when(commissionMapper.selectList(any())).thenAnswer(i -> new java.util.ArrayList<>(ledger));
        when(commissionMapper.update(isNull(),any())).thenReturn(1);
        when(commissionMapper.insert(any(MktPromoterCommission.class))).thenAnswer(i -> { ledger.add(i.getArgument(0)); return 1; });
        service.refreezeByOrder(100L);
        service.clawback(100L,"90天内退租");
        service.clawback(100L,"重复退租事件");
        assertThat(service.unfreezeByOrderInTransaction(100L)).isZero();
        assertThat(ledger).hasSize(2);
        assertThat(original.getReceiptSuspended()).isEqualTo(2);
        assertThat(ledger.stream().map(MktPromoterCommission::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isZero();
    }

    @Test void reversalCancelsPendingWithdrawalThatContainsRestorationCredit() {
        var original = commission(11L,MktCommissionService.C_WITHDRAWN,"100"); original.setWithdrawalId(88L); original.setReceiptRevision(1);
        var credit = commission(12L,MktCommissionService.C_SETTLED,"100"); credit.setAdjustmentSequence(1); credit.setWithdrawalId(99L);
        when(commissionMapper.selectList(any())).thenReturn(List.of(original,credit));
        when(commissionMapper.update(isNull(),any())).thenReturn(1); when(withdrawalMapper.update(isNull(),any())).thenReturn(1);
        service.refreezeByOrder(100L);
        verify(withdrawalMapper).update(isNull(),any());
        var cap = ArgumentCaptor.forClass(MktPromoterCommission.class); verify(commissionMapper).insert(cap.capture());
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("-100");
        assertThat(cap.getValue().getAdjustmentSequence()).isEqualTo(2);
        assertThat(credit.getWithdrawalId()).isNull();
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
        c.setId(id); c.setStatus(status); c.setSign(1); c.setAdjustmentSequence(0); c.setReceiptRevision(0); c.setReceiptSuspended(0); c.setAmount(new BigDecimal(amount)); c.setPromoterId(1L);
        c.setPositionCode("P1"); c.setSharePct(50); c.setDiffPct(50); c.setRate(new BigDecimal("0.5"));
        c.setBaseAmount(BigDecimal.TEN); c.setReferralOrderId(100L);
        return c;
    }
}
