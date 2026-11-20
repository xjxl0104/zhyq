package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktWithdrawalServiceTest {
    @Mock MktWithdrawalMapper withdrawalMapper;
    @Mock MktPromoterCommissionMapper commissionMapper;
    @Mock MktPromoterMapper promoterMapper;
    @Mock BizSettings bizSettings;
    @Mock MktAuditService auditService;
    @Mock com.zhyq.park.marketing.mapper.MktPromoterAccountMapper accountMapper;
    @Mock MktPaymentProofService proofs;
    MktWithdrawalService service;

    @BeforeAll static void mp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktPromoterCommission.class);
        TableInfoHelper.initTableInfo(a, MktWithdrawal.class);
    }
    @BeforeEach void setUp() {
        service = new MktWithdrawalService(withdrawalMapper, commissionMapper, promoterMapper, bizSettings, auditService, accountMapper, proofs);
        lenient().when(bizSettings.getDecimal(eq("marketing"), eq("min_withdraw"), any(BigDecimal.class))).thenReturn(new BigDecimal("100"));
        lenient().when(bizSettings.getInt(eq("marketing"), eq("tax_mode"), anyInt())).thenReturn(0);
        lenient().when(promoterMapper.selectForUpdate(1L)).thenReturn(promoter());
        lenient().when(withdrawalMapper.insert(any(MktWithdrawal.class))).thenAnswer(i -> { ((MktWithdrawal)i.getArgument(0)).setId(9L); return 1; });
        lenient().when(withdrawalMapper.updateById(any(MktWithdrawal.class))).thenReturn(1);
        var account=new com.zhyq.park.marketing.entity.MktPromoterAccount();account.setId(2L);account.setPromoterId(1L);account.setReviewStatus(1);
        account.setVerifiedAt(java.time.LocalDateTime.now());account.setAccountNoEnc("encrypted-original");account.setRealName("张某");account.setAccountTail("1234");
        lenient().when(accountMapper.selectOne(any())).thenReturn(account);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.01", "1.00", "99.99" })
    void positiveCentApplicationsIgnoreLegacyHundredYuanMinimum(String amount) {
        // Production may retain the old setting: it must not silently reintroduce the review-blocking threshold.
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "100.00")));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        var withdrawal = service.apply(1L, new BigDecimal(amount));
        assertThat(withdrawal.getAmount()).isEqualByComparingTo(amount);
        assertThat(withdrawal.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(withdrawal.getNetAmount()).isEqualByComparingTo(amount);
        assertThat(withdrawal.getStatus()).isEqualTo(MktWithdrawalService.WS_PENDING);
        verify(bizSettings, never()).getDecimal(eq("marketing"), eq("min_withdraw"), any());
        verifyNoInteractions(proofs);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-0.01", "0.001", "0.009", "1.001" })
    void applicationRejectsNonPositiveAndSubcentAmountsBeforeCreatingWithdrawal(String amount) {
        assertThatThrownBy(() -> service.apply(1L, new BigDecimal(amount)))
                .isInstanceOf(BizException.class).hasMessageContaining("最多两位小数");
        verify(withdrawalMapper, never()).insert(any(MktWithdrawal.class));
        verifyNoInteractions(commissionMapper);
    }

    @Test void centApplicationStillCannotExceedTheAvailableBalance() {
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "0.009")));
        assertThatThrownBy(() -> service.apply(1L, new BigDecimal("0.01")))
                .isInstanceOf(BizException.class).hasMessageContaining("余额不足");
        verify(withdrawalMapper, never()).insert(any(MktWithdrawal.class));
    }

    @Test void publishedRulesDescribeTheDefaultWindow() {
        when(bizSettings.getString(eq("marketing"), eq("withdraw_arrival_time"), anyString()))
                .thenAnswer(call -> call.getArgument(2));
        when(bizSettings.getInt(eq("marketing"), anyString(), anyInt()))
                .thenAnswer(call -> call.getArgument(2));
        var rules = service.withdrawalRules();
        assertThat(rules).containsEntry("minimumAmount", new BigDecimal("0.01"))
                .containsEntry("dailyLimit", 0)
                .containsEntry("feeAmount", BigDecimal.ZERO)
                .containsEntry("applicationTime", "每月 25–30 日开放提现申请")
                .containsEntry("payoutMethod", "财务审批通过后转账至已审核的收款账户")
                .containsEntry("arrivalTime", MktWithdrawalService.DEFAULT_ARRIVAL_TIME);
        verify(bizSettings, never()).getDecimal(eq("marketing"), eq("min_withdraw"), any());
    }

    @Test void publishedArrivalTimeUsesTheConfiguredOperationalPolicy() {
        when(bizSettings.getString(eq("marketing"), eq("withdraw_arrival_time"), anyString()))
                .thenReturn("测试配置的实际处理时效");
        assertThat(service.withdrawalRules()).containsEntry("arrivalTime", "测试配置的实际处理时效");
    }

    @Test void thousandthsAccumulateIntoExactCentWithoutTruncation() {
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L,"99.993"), row(2L,"0.007")));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        var withdrawal = service.apply(1L,new BigDecimal("100.00"));
        org.assertj.core.api.Assertions.assertThat(withdrawal.getAmount()).isEqualByComparingTo("100.00");
        verify(commissionMapper,times(2)).update(isNull(),any(Wrapper.class));
    }

    @Test void applyLocksOnlyRowsNeededForRequestedAmount() {
        MktPromoterCommission a = row(1L, "100");
        MktPromoterCommission b = row(2L, "900");
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.apply(1L, new BigDecimal("100"));
        verify(commissionMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test void applyRollsBackWhenConcurrentClaimWins() {
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "100")));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> service.apply(1L, new BigDecimal("100")))
                .isInstanceOf(BizException.class).hasMessageContaining("锁定");
    }

    @Test void applyMayReservePartOfOneCommissionWithoutChangingOriginalEntitlement() {
        var original = row(1L, "100.001");
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        var withdrawal = service.apply(1L,new BigDecimal("100.00"));
        org.assertj.core.api.Assertions.assertThat(original.getAmount()).isEqualByComparingTo("100.001");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawalAmount()).isEqualByComparingTo("100.00");
        org.assertj.core.api.Assertions.assertThat(withdrawal.getCommissionAllocations()).contains("\"amount\":100.00");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawnAmount()).isNull();
    }

    @Test void partialPaymentReleasesExactRemainderAndKeepsSettlementStatus() {
        var original = row(1L,"100.001"); original.setWithdrawalId(9L); original.setWithdrawalAmount(new BigDecimal("100.00"));
        MktWithdrawal w = approvedWithdrawal("100.00");
        mockPay(w,List.of(original));
        service.pay(9L,"PARTIAL","file:10","finance");
        org.assertj.core.api.Assertions.assertThat(original.getAmount()).isEqualByComparingTo("100.001");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawnAmount()).isEqualByComparingTo("100.00");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawalId()).isNull();
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawalAmount()).isNull();
        org.assertj.core.api.Assertions.assertThat(original.getStatus()).isEqualTo(MktCommissionService.C_SETTLED);
        org.assertj.core.api.Assertions.assertThat(service.balance(1L)).isEqualByComparingTo("0.001");
    }

    @Test void carriedThousandthCanCombineWithNextCommissionWithoutRepayingOldHundred() {
        var original = row(1L,"100.001"); original.setWithdrawnAmount(new BigDecimal("100.000"));
        var next = row(2L,"99.999");
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original,next));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        var withdrawal = service.apply(1L,new BigDecimal("100.00"));
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawalAmount()).isEqualByComparingTo("0.001");
        org.assertj.core.api.Assertions.assertThat(next.getWithdrawalAmount()).isEqualByComparingTo("99.999");
        org.assertj.core.api.Assertions.assertThat(withdrawal.getAmount()).isEqualByComparingTo("100.00");
        org.assertj.core.api.Assertions.assertThat(service.remainingAmount(original)).isEqualByComparingTo("0.001");
    }

    @Test void refundDebtAfterPartialPaymentIsFullyOffsetBeforeAnotherWithdrawal() {
        var original = row(1L,"100.001"); original.setWithdrawnAmount(new BigDecimal("100.000"));
        var debit = row(2L,"-100.001"); debit.setSign(-1); debit.setStatus(MktCommissionService.C_SETTLEABLE);
        var next = row(3L,"200.001");
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original,debit,next));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        service.apply(1L,new BigDecimal("100.00"));
        org.assertj.core.api.Assertions.assertThat(debit.getWithdrawalAmount()).isEqualByComparingTo("-100.001");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawalAmount()).isEqualByComparingTo("0.001");
        org.assertj.core.api.Assertions.assertThat(next.getWithdrawalAmount()).isEqualByComparingTo("200.000");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawnAmount()).isEqualByComparingTo("100.000");
    }

    @Test void replayedPartialPaymentDoesNotConsumeReleasedRemainderAgain() {
        var original = row(1L,"100.001"); original.setWithdrawalId(9L); original.setWithdrawalAmount(new BigDecimal("100.00"));
        var w = approvedWithdrawal("100.00"); mockPay(w,List.of(original));
        service.pay(9L,"PARTIAL-ONCE","file:10","finance");
        w.setStatus(MktWithdrawalService.WS_PAID); w.setPayProof("file:10");
        org.mockito.Mockito.doReturn(w).when(withdrawalMapper).selectOne(any());
        service.pay(9L,"PARTIAL-ONCE","file:10","finance");
        verify(commissionMapper,times(1)).update(isNull(),any(Wrapper.class));
        org.assertj.core.api.Assertions.assertThat(MktWithdrawalService.remainingAmount(original)).isEqualByComparingTo("0.001");
    }

    @Test void rejectedPartialWithdrawalClearsReservationWithoutChangingCumulativePaidAmount() {
        var original = row(1L,"200.001"); original.setWithdrawnAmount(new BigDecimal("100.00"));
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        var withdrawal = service.apply(1L,new BigDecimal("100.00"));
        when(withdrawalMapper.selectById(9L)).thenReturn(withdrawal);
        when(withdrawalMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        service.reject(9L,"资料待核实","ops");
        var cap = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(commissionMapper,times(2)).update(isNull(),cap.capture());
        var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) cap.getAllValues().get(1);
        org.assertj.core.api.Assertions.assertThat(update.getSqlSet()).contains("withdrawal_id=", "withdrawal_amount=").doesNotContain("withdrawn_amount=");
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawnAmount()).isEqualByComparingTo("100.00");
    }

    @Test void legacyWholeRowPendingPaymentStillWorksWithoutReservationSnapshot() {
        var original = row(1L,"100.00"); original.setWithdrawalId(9L);
        mockPay(approvedWithdrawal("100.00"),List.of(original));
        service.pay(9L,"LEGACY","file:10","finance");
        org.assertj.core.api.Assertions.assertThat(original.getStatus()).isEqualTo(MktCommissionService.C_WITHDRAWN);
        org.assertj.core.api.Assertions.assertThat(original.getWithdrawnAmount()).isEqualByComparingTo("100.00");
    }

    @Test void concurrentChangeDuringPartialPaymentRollsBackInsteadOfAcknowledgingPayout() {
        var original = row(1L,"100.001"); original.setWithdrawalId(9L); original.setWithdrawalAmount(new BigDecimal("100.00"));
        mockPay(approvedWithdrawal("100.00"),List.of(original));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> service.pay(9L,"RACE","file:10","finance")).hasMessageContaining("回滚");
    }

    private static MktWithdrawal approvedWithdrawal(String amount) {
        var w = new MktWithdrawal(); w.setId(9L); w.setPromoterId(1L); w.setStatus(2); w.setAmount(new BigDecimal(amount));
        w.setAccountNoEnc("encrypted"); w.setAccountVerifiedAt(java.time.LocalDateTime.now()); return w;
    }
    private void mockPay(MktWithdrawal w, List<MktPromoterCommission> rows) {
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
        when(withdrawalMapper.selectOne(any())).thenAnswer(inv -> ((Wrapper<?>) inv.getArgument(0)).getSqlSegment().contains("FOR UPDATE") ? w : null);
        when(withdrawalMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(rows);
        lenient().when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
    }

    @Test void newlyFrozenPartnerCannotReceivePendingPayout() {
        var w=new MktWithdrawal();w.setId(9L);w.setPromoterId(1L);w.setStatus(2);
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
        when(withdrawalMapper.selectOne(any())).thenAnswer(inv -> ((Wrapper<?>) inv.getArgument(0)).getSqlSegment().contains("FOR UPDATE") ? w : null);
        var p=promoter();p.setStatus(2);when(promoterMapper.selectForUpdate(1L)).thenReturn(p);
        assertThatThrownBy(()->service.pay(9L,"PAY-FROZEN","file:10","ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("冻结或退出");
        verify(withdrawalMapper,never()).update(any(),any());
        org.mockito.Mockito.verifyNoInteractions(proofs);
    }

    @Test void payRejectsWhenLinkedRowsDoNotEqualWithdrawalAmount() {
        MktWithdrawal w = new MktWithdrawal(); w.setId(9L); w.setPromoterId(1L);w.setStatus(2);w.setAccountNoEnc("encrypted");w.setAccountVerifiedAt(java.time.LocalDateTime.now()); w.setAmount(new BigDecimal("100"));
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
        when(withdrawalMapper.selectOne(any())).thenAnswer(inv -> ((Wrapper<?>) inv.getArgument(0)).getSqlSegment().contains("FOR UPDATE") ? w : null);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "90")));
        assertThatThrownBy(() -> service.pay(9L, "PAY-1", "file:10", "ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("金额不一致");
    }

    @Test void manualApplicationCannotBypassAccountReview() {
        var p=promoter();p.setIdVerified(0);when(promoterMapper.selectForUpdate(1L)).thenReturn(p);
        assertThatThrownBy(()->service.apply(1L,new BigDecimal("100"))).isInstanceOf(BizException.class).hasMessageContaining("审核");
        verify(withdrawalMapper,never()).insert(any(MktWithdrawal.class));
    }
    @Test void fullWithdrawalIncludesDebitsAfterPositiveRowsAndSnapshotsAccount() {
        var debit=row(3L,"-20");debit.setStatus(MktCommissionService.C_SETTLEABLE);debit.setSign(-1);
        when(commissionMapper.selectList(any())).thenReturn(List.of(row(1L,"100"),row(2L,"100"),debit));
        when(commissionMapper.update(isNull(),any(Wrapper.class))).thenReturn(1);
        var result=service.apply(1L,new BigDecimal("180.00"));
        org.assertj.core.api.Assertions.assertThat(result.getAccountNoEnc()).isEqualTo("encrypted-original");
        org.assertj.core.api.Assertions.assertThat(result.getAccountTail()).isEqualTo("1234");
        verify(commissionMapper,times(3)).update(isNull(),any(Wrapper.class));
    }
    @Test void reusedPaymentNumberCannotAcknowledgeAnotherWithdrawal() {
        MktWithdrawal requested=new MktWithdrawal();requested.setId(9L);requested.setPromoterId(1L);
        MktWithdrawal other=new MktWithdrawal();other.setId(10L);other.setStatus(3);other.setPayProof("file:10");
        when(withdrawalMapper.selectById(9L)).thenReturn(requested);when(withdrawalMapper.selectOne(any())).thenAnswer(inv -> ((Wrapper<?>) inv.getArgument(0)).getSqlSegment().contains("FOR UPDATE") ? requested : other);
        assertThatThrownBy(()->service.pay(9L,"DUP","file:10","finance")).isInstanceOf(BizException.class).hasMessageContaining("其他提现");
        verify(withdrawalMapper,never()).update(any(),any());
    }
    private static MktPromoter promoter() { MktPromoter p = new MktPromoter(); p.setId(1L); p.setStatus(1); p.setIdVerified(1); return p; }
    private static MktPromoterCommission row(Long id, String amount) { MktPromoterCommission c = new MktPromoterCommission(); c.setId(id); c.setPromoterId(1L); c.setAmount(new BigDecimal(amount)); c.setSign(1); c.setStatus(MktCommissionService.C_SETTLED); return c; }

    @Test void withdrawalWindowIsInclusiveAndZeroStartMeansNoLimit() {
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 10, 24))).isFalse();
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 10, 30))).isTrue();
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 10, 31))).isFalse();
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(MktWithdrawalService.inWindow(25, 30, java.time.LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(MktWithdrawalService.inWindow(0, 0, java.time.LocalDate.of(2026, 10, 11))).isTrue();
    }

    @Test void autoWithdrawDayIsClampedIntoApplicationWindow() {
        // 历史配置 auto_withdraw_day=10;新窗口 25-30 时自动提现必须落进窗口内,不能绕过限制
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_start_day"), anyInt())).thenReturn(25);
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_end_day"), anyInt())).thenReturn(30);
        when(bizSettings.getInt(eq("marketing"), eq("auto_withdraw_day"), anyInt())).thenReturn(10);
        assertThat(service.autoWithdrawDay()).isEqualTo(25);
        // 已落在窗口内的配置保持不变
        when(bizSettings.getInt(eq("marketing"), eq("auto_withdraw_day"), anyInt())).thenReturn(28);
        assertThat(service.autoWithdrawDay()).isEqualTo(28);
        // 0 = 关闭,不参与 clamp
        when(bizSettings.getInt(eq("marketing"), eq("auto_withdraw_day"), anyInt())).thenReturn(0);
        assertThat(service.autoWithdrawDay()).isZero();
        // 不限制申请窗口(start<=0)时沿用配置
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_start_day"), anyInt())).thenReturn(0);
        when(bizSettings.getInt(eq("marketing"), eq("auto_withdraw_day"), anyInt())).thenReturn(10);
        assertThat(service.autoWithdrawDay()).isEqualTo(10);
    }

    @Test void submittedApplicationEntersTheWithdrawalApprovalFlow() {
        var workflow = mock(com.zhyq.park.workflow.service.WorkflowService.class);
        service.setWorkflowService(workflow);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "50.00")));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.apply(1L, new BigDecimal("20.00"));
        verify(workflow).startBySystem("withdrawal", 9L);
    }

    @Test void automaticApplicationIgnoresTheApplicationWindow() {
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_start_day"), anyInt())).thenReturn(40);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "50.00")));
        when(commissionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertThatThrownBy(() -> service.apply(1L, new BigDecimal("20.00"))).hasMessageContaining("开放提现申请");
        assertThat(service.apply(1L, new BigDecimal("20.00"), true).getStatus()).isEqualTo(MktWithdrawalService.WS_PENDING);
        verify(auditService).log(eq("withdrawal.auto-apply"), eq("withdrawal"), eq(9L), anyString());
    }

    @Test void consoleApprovalUsesTheCurrentWorkflowTaskWhenOneIsPending() {
        var workflow = mock(com.zhyq.park.workflow.service.WorkflowService.class);
        service.setWorkflowService(workflow);
        var task = new com.zhyq.park.workflow.entity.WfTask(); task.setId(77L);
        when(workflow.currentPendingTask("withdrawal", 5L)).thenReturn(task);
        service.approveFromConsole(5L, null, "finance");
        service.rejectFromConsole(5L, "账户有误", "finance");
        verify(workflow).approve(77L, "同意");
        verify(workflow).reject(77L, "账户有误");
        verify(withdrawalMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test void consoleRejectionRequiresAReason() {
        assertThatThrownBy(() -> service.rejectFromConsole(5L, " ", "finance")).hasMessageContaining("驳回必须填写原因");
    }

    @Test void rulesDescribeTheConfiguredWindow() {
        // 使用与默认值不同的窗口,验证规则参数覆盖仍生效(默认 25-30 由另一用例覆盖)
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_start_day"), anyInt())).thenReturn(5);
        when(bizSettings.getInt(eq("marketing"), eq("withdraw_window_end_day"), anyInt())).thenReturn(12);
        when(bizSettings.getString(eq("marketing"), eq("withdraw_arrival_time"), anyString()))
                .thenAnswer(call -> call.getArgument(2));
        assertThat(service.withdrawalRules()).containsEntry("applicationTime", "每月 5–12 日开放提现申请")
                .containsKey("applicationOpen");
    }
}
