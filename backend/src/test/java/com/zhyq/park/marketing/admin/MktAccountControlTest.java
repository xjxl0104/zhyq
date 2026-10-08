package com.zhyq.park.marketing.admin;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktAccountControlTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MktAccountControl control = spy(new MktAccountControl(jdbc));
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void login(String subject, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority(role))));
    }
    @Test void callerCannotElevateUsingNameOrTokenRole() {
        for (String subject : List.of("wh:2", "admin", "mp:invalid", "mp:0")) {
            login(subject, "ROLE_MP"); assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("管理");
        }
        login("mp:2", "ROLE_admin"); assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("超级管理员");
        verifyNoInteractions(jdbc);
    }
    @Test void ordinaryPartnerAndDisabledAdminCannotManageAccounts() {
        login("mp:2", "ROLE_MP");
        doReturn(new MktAccountControl.State(false,false,0)).when(control).state("mp",2L);
        assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("超级管理员");
        doReturn(new MktAccountControl.State(true,true,2)).when(control).state("mp",2L);
        assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("超级管理员");
        verifyNoInteractions(jdbc);
    }
    @Test void grantMustBeLiveAndPartnerMustBeNormal() {
        login("mp:2", "ROLE_MP");
        doReturn(new MktAccountControl.State(true,false,1)).when(control).state("mp",2L);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(2L))).thenReturn(1);
        assertThat(control.requireSuperAdmin()).isEqualTo(2L);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(2L))).thenReturn(0);
        assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("正常状态");
        doReturn(new MktAccountControl.State(false,false,2)).when(control).state("mp",2L);
        assertThatThrownBy(control::requireSuperAdmin).hasMessageContaining("超级管理员");
    }
}
