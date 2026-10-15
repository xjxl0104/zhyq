package com.zhyq.park.marketing.controller;

import com.zhyq.park.marketing.service.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.common.setting.BizSettings;
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
import org.springframework.test.util.AopTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MktServiceContractDeletionSecurityTest.Beans.class)
class MktServiceContractDeletionSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Beans {
        @Bean MktServiceContractDeletionService deletion() { return mock(MktServiceContractDeletionService.class); }
        @Bean MktServiceContractController controller(MktServiceContractDeletionService deletion) {
            return new MktServiceContractController(mock(MktServiceContractMapper.class), mock(MktServiceContractVersionMapper.class),
                    mock(MktContractWarehouseMapper.class), mock(MktWarehouseMapper.class), mock(CustomerMapper.class),
                    mock(MktServiceContractService.class), deletion, mock(MktWarehouseOnboardingService.class), mock(BizSettings.class), mock(MktWarehouseFileService.class));
        }
    }
    @Autowired MktServiceContractController controller;
    @Autowired MktServiceContractDeletionService deletion;
    @Test @WithMockUser(roles="admin")
    void administratorCanDelete() {
        assertThat(controller.delete(9L, Map.of("reason","误录")).getCode()).isZero();
        verify(AopTestUtils.<MktServiceContractDeletionService>getUltimateTargetObject(deletion)).delete(9L,"误录");
    }
    @Test @WithMockUser(authorities={"crm:marketing:contract:edit","crm:marketing:contract:audit"})
    void editAndAuditPermissionDoesNotGrantDeletion() {
        assertThatThrownBy(() -> controller.delete(8L, Map.of("reason","误录"))).isInstanceOf(AccessDeniedException.class);
        verify(AopTestUtils.<MktServiceContractDeletionService>getUltimateTargetObject(deletion), never()).delete(eq(8L), any());
    }
    @Test @WithMockUser(roles="WH")
    void warehouseCannotDeleteContracts() {
        assertThatThrownBy(() -> controller.delete(7L, Map.of("reason","误录"))).isInstanceOf(AccessDeniedException.class);
    }
}
