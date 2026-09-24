package com.zhyq.park.marketing.controller;

import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MktPromoterDeletionSecurityTest.Beans.class)
class MktPromoterDeletionSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Beans {
        @Bean MktPromoterDeletionService deletionService() { return mock(MktPromoterDeletionService.class); }
        @Bean MktPromoterController controller(MktPromoterDeletionService deletion) {
            return new MktPromoterController(mock(MktPromoterAccountService.class), mock(MktPromoterMapper.class),
                    mock(MktPromoterService.class), deletion, mock(MktPositionReviewService.class), mock(MktPositionHistoryMapper.class),
                    mock(MktPromoterCommissionMapper.class), mock(MktReferralOrderMapper.class), mock(MktWithdrawalMapper.class), mock(CustomerMapper.class));
        }
    }
    @Autowired MktPromoterController controller;
    @Autowired MktPromoterDeletionService deletion;
    @BeforeEach void resetService() { reset(deletion); }

    @Test @WithMockUser(roles = "admin")
    void administratorMayDeleteWithoutAnyGeneralEditPermission() {
        assertThat(controller.delete(7L, new MktPromoterController.DeleteRequest("误注册")).getCode()).isZero();
        verify(deletion).delete(7L, "误注册");
    }

    @Test @WithMockUser(authorities = {"crm:marketing:promoter:query", "crm:marketing:promoter:edit", "crm:marketing:promoter:delete"})
    void editAndInventedDeletePermissionCannotBypassAdministratorRole() {
        assertThatThrownBy(() -> controller.delete(7L, new MktPromoterController.DeleteRequest("误注册")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(deletion);
    }

    @Test @WithMockUser(roles = {"MP", "manager"})
    void partnerAndManagerRolesCannotDelete() {
        assertThatThrownBy(() -> controller.delete(7L, new MktPromoterController.DeleteRequest("误注册")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(deletion);
    }
}
