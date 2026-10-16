package com.zhyq.park.marketing.controller;

import com.zhyq.park.marketing.service.*;
import com.zhyq.park.marketing.mapper.*;
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
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MktWarehouseDeletionSecurityTest.Beans.class)
class MktWarehouseDeletionSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Beans {
        @Bean MktWarehouseDeletionService deletion() { return mock(MktWarehouseDeletionService.class); }
        @Bean MktWarehouseController controller(MktWarehouseDeletionService deletion) {
            return new MktWarehouseController(mock(MktWarehouseMapper.class), mock(MktWarehouseOnboardingMapper.class),
                    mock(MktServiceContractMapper.class), mock(MktWarehouseOnboardingService.class), mock(MktAuditService.class),
                    mock(MktWarehouseFileService.class), deletion, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        }
    }
    @Autowired MktWarehouseController controller;
    @Autowired MktWarehouseDeletionService deletion;
    @Test @WithMockUser(roles = "admin")
    void administratorMayDeleteImmediately() {
        assertThat(controller.delete(7L,Map.of("reason","误录")).getCode()).isZero();
        verify(deletion).request(7L,"误录");
    }
    @Test @WithMockUser(authorities = {"crm:marketing:warehouse:edit","crm:marketing:warehouse:audit"})
    void ordinaryEditorCannotDelete() {
        assertThatThrownBy(() -> controller.delete(8L,Map.of("reason","误录"))).isInstanceOf(AccessDeniedException.class);
        verify(deletion,never()).request(eq(8L),any());
    }
    @Test @WithMockUser(roles = "WH")
    void warehouseIdentityCannotDeleteOrReadAdminMaintenanceRecords() {
        assertThatThrownBy(() -> controller.delete(9L,Map.of("reason","误录"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.maintenance(1,20,"",null)).isInstanceOf(AccessDeniedException.class);
    }
}
