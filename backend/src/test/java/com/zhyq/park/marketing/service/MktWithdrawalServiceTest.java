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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test void applyRejectsPartialRequestAgainstIndivisibleCommissionRow() {
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "1000")));
        assertThatThrownBy(() -> service.apply(1L, new BigDecimal("100")))
                .isInstanceOf(BizException.class).hasMessageContaining("完整流水");
        verify(commissionMapper, never()).update(isNull(), any(Wrapper.class));
        verify(withdrawalMapper, never()).insert(any(MktWithdrawal.class));
    }

    @Test void newlyFrozenPartnerCannotReceivePendingPayout() {
        var w=new MktWithdrawal();w.setId(9L);w.setPromoterId(1L);w.setStatus(2);
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
        var p=promoter();p.setStatus(2);when(promoterMapper.selectForUpdate(1L)).thenReturn(p);
        assertThatThrownBy(()->service.pay(9L,"PAY-FROZEN","file:10","ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("冻结或退出");
        verify(withdrawalMapper,never()).update(any(),any());
        org.mockito.Mockito.verifyNoInteractions(proofs);
    }

    @Test void payRejectsWhenLinkedRowsDoNotEqualWithdrawalAmount() {
        MktWithdrawal w = new MktWithdrawal(); w.setId(9L); w.setPromoterId(1L);w.setStatus(2);w.setAccountNoEnc("encrypted");w.setAccountVerifiedAt(java.time.LocalDateTime.now()); w.setAmount(new BigDecimal("100"));
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
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
        when(withdrawalMapper.selectById(9L)).thenReturn(requested);when(withdrawalMapper.selectOne(any())).thenReturn(other);
        assertThatThrownBy(()->service.pay(9L,"DUP","file:10","finance")).isInstanceOf(BizException.class).hasMessageContaining("其他提现");
        verify(withdrawalMapper,never()).update(any(),any());
    }
    private static MktPromoter promoter() { MktPromoter p = new MktPromoter(); p.setId(1L); p.setStatus(1); p.setIdVerified(1); return p; }
    private static MktPromoterCommission row(Long id, String amount) { MktPromoterCommission c = new MktPromoterCommission(); c.setId(id); c.setPromoterId(1L); c.setAmount(new BigDecimal(amount)); c.setSign(1); c.setStatus(MktCommissionService.C_SETTLED); return c; }
}
