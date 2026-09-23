package com.zhyq.park.crm;

import com.zhyq.park.crm.controller.CustomerController;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.crm.mapper.LeadMapper;
import com.zhyq.park.marketing.service.MktCustomerAssignmentService;
import com.zhyq.park.marketing.service.MktLockService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CustomerAuthorizationTest.Beans.class)
class CustomerAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Beans {
        @Bean CustomerMapper customers() { return mock(CustomerMapper.class); }
        @Bean LeadMapper leads() { return mock(LeadMapper.class); }
        @Bean CustomerController controller(CustomerMapper customers, LeadMapper leads) {
            return new CustomerController(customers, leads, mock(MktCustomerAssignmentService.class), mock(MktLockService.class));
        }
    }
    @Autowired CustomerController controller;
    @Autowired CustomerMapper customers;
    @Autowired LeadMapper leads;
    @BeforeEach void resetMocks() { reset(customers, leads); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void noBusinessPermissionsDenyEveryCustomerActionBeforeAnyDatabaseAccess() {
        login();
        Customer body = new Customer(); body.setId(1L);
        assertThatThrownBy(() -> controller.page(1, 10, null, null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.add(body)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.update(body)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.delete(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.sign(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.lose(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.fromLead(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers, leads);
    }

    @Test void QueryGrantDoesNotGrantWritesAndLeadConversionRequiresBothModules() {
        login("crm:customer:query");
        assertThatCode(() -> controller.get(1L)).doesNotThrowAnyException();
        verify(customers).selectById(1L);
        assertThatThrownBy(() -> controller.delete(1L)).isInstanceOf(AccessDeniedException.class);
        login("crm:customer:add");
        assertThatThrownBy(() -> controller.fromLead(1L)).isInstanceOf(AccessDeniedException.class);
        login("crm:lead:edit");
        assertThatThrownBy(() -> controller.fromLead(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(leads);
    }

    private static void login(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ops", null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
}
