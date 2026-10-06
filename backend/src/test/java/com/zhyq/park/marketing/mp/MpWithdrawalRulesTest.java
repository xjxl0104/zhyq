package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MpWithdrawalRulesTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void balancePublishesTheSameCentPolicyAsApplicationsAndPreservesFractionalCredit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:7", null, List.of()));
        var promoters = mock(MktPromoterMapper.class);
        var commissions = mock(MktPromoterCommissionMapper.class);
        var withdrawals = mock(MktWithdrawalMapper.class);
        var settings = mock(BizSettings.class);
        var me = new MktPromoter(); me.setId(7L); me.setIdVerified(0);
        when(promoters.selectById(7L)).thenReturn(me);
        var commission = new MktPromoterCommission(); commission.setAmount(new BigDecimal("0.019"));
        when(commissions.selectList(any())).thenReturn(List.of(commission));
        when(settings.getString(eq("marketing"), eq("withdraw_arrival_time"), anyString())).thenAnswer(call -> call.getArgument(2));
        var service = new MktWithdrawalService(withdrawals, commissions, promoters, settings, mock(MktAuditService.class),
                mock(MktPromoterAccountMapper.class), mock(MktPaymentProofService.class));
        var controller = new MpBizController(mock(CustomerMapper.class), promoters, mock(MktWarehouseMapper.class),
                commissions, mock(MktReferralOrderMapper.class), withdrawals, mock(TenantMessageMapper.class),
                mock(MktLockService.class), service, settings, mock(MktAuditService.class),
                mock(MktCustomerAssignmentService.class), mock(MktCustomerDeletionService.class));

        var result = controller.balance();
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).containsEntry("balance", new BigDecimal("0.019"))
                .containsEntry("cashableBalance", new BigDecimal("0.01"))
                .containsEntry("fractionalBalance", new BigDecimal("0.009"))
                .containsEntry("minWithdraw", MktWithdrawalService.MIN_WITHDRAWAL)
                .containsEntry("idVerified", 0);
        assertThat((Map<?, ?>) result.getData().get("withdrawalRules")).isEqualTo(service.withdrawalRules());
        verify(settings, never()).getDecimal(eq("marketing"), eq("min_withdraw"), any());
        verifyNoInteractions(withdrawals);
    }
}
