package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MpReferralDeletionQuotaTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    final BizSettings settings = mock(BizSettings.class);
    final MktLockService locks = mock(MktLockService.class);
    final MpBizController controller = new MpBizController(customers, promoters, mock(MktWarehouseMapper.class),
            mock(MktPromoterCommissionMapper.class), mock(MktReferralOrderMapper.class), mock(MktWithdrawalMapper.class),
            mock(TenantMessageMapper.class), locks, mock(MktWithdrawalService.class), settings,
            mock(MktAuditService.class), mock(MktCustomerAssignmentService.class), mock(MktCustomerDeletionService.class));

    @BeforeEach void fixture() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:7", null, List.of()));
        MktPromoter me = new MktPromoter(); me.setId(7L); me.setStatus(1); me.setPhone("13800000007");
        when(promoters.selectForUpdate(7L)).thenReturn(me);
        when(settings.getInt("marketing", "daily_referral_cap", 20)).thenReturn(20);
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void deletingAllActiveCustomersDoesNotResetDailyQuota() {
        // 活跃客户 selectCount 默认为零，但包含已删除档案的当天推荐次数已满。
        when(customers.countReferralsSince(eq(7L), any())).thenReturn(20L);
        assertThatThrownBy(() -> controller.referral(Map.of("name", "再次报备", "phone", "13800000042")))
                .hasMessageContaining("今日推荐已达上限 20");
        verify(customers).countReferralsSince(7L, LocalDate.now().atStartOfDay());
        verify(customers, never()).insert(any(Customer.class));
        verifyNoInteractions(locks);
    }

    @Test void customerMayBeReferredAgainAfterDeletionWhenDailyQuotaRemains() {
        when(customers.countReferralsSince(eq(7L), any())).thenReturn(19L);
        when(customers.insert(any(Customer.class))).thenAnswer(inv -> {
            ((Customer) inv.getArgument(0)).setId(42L); return 1;
        });
        MktCustomerLock lock = new MktCustomerLock(); lock.setStatus(1);
        when(locks.prelock(42L, 7L)).thenReturn(lock);
        assertThat(controller.referral(Map.of("name", "再次报备", "phone", "13800000042")).getCode()).isZero();
        verify(customers).insert(any(Customer.class));
    }
}
