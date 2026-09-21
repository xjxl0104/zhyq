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
    MktWithdrawalService service;

    @BeforeAll static void mp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktPromoterCommission.class);
        TableInfoHelper.initTableInfo(a, MktWithdrawal.class);
    }
    @BeforeEach void setUp() {
        service = new MktWithdrawalService(withdrawalMapper, commissionMapper, promoterMapper, bizSettings, auditService);
        lenient().when(bizSettings.getDecimal(eq("marketing"), eq("min_withdraw"), any(BigDecimal.class))).thenReturn(new BigDecimal("100"));
        lenient().when(bizSettings.getInt(eq("marketing"), eq("tax_mode"), anyInt())).thenReturn(0);
        lenient().when(promoterMapper.selectById(1L)).thenReturn(promoter());
        lenient().when(withdrawalMapper.insert(any(MktWithdrawal.class))).thenAnswer(i -> { ((MktWithdrawal)i.getArgument(0)).setId(9L); return 1; });
        lenient().when(withdrawalMapper.updateById(any(MktWithdrawal.class))).thenReturn(1);
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

    @Test void payRejectsWhenLinkedRowsDoNotEqualWithdrawalAmount() {
        MktWithdrawal w = new MktWithdrawal(); w.setId(9L); w.setAmount(new BigDecimal("100"));
        when(withdrawalMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(withdrawalMapper.selectById(9L)).thenReturn(w);
        when(commissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(1L, "90")));
        assertThatThrownBy(() -> service.pay(9L, "PAY-1", "ops"))
                .isInstanceOf(BizException.class).hasMessageContaining("金额不一致");
    }

    private static MktPromoter promoter() { MktPromoter p = new MktPromoter(); p.setId(1L); p.setStatus(1); return p; }
    private static MktPromoterCommission row(Long id, String amount) { MktPromoterCommission c = new MktPromoterCommission(); c.setId(id); c.setPromoterId(1L); c.setAmount(new BigDecimal(amount)); c.setSign(1); c.setStatus(MktCommissionService.C_SETTLED); return c; }
}
