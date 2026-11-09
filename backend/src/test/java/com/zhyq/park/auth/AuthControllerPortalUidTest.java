package com.zhyq.park.auth;

import com.zhyq.park.auth.mapper.AuthQueryMapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysMenu;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerPortalUidTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final AuthQueryMapper permissions = mock(AuthQueryMapper.class);
    private final AuthController controller = new AuthController(
            users, permissions, mock(JwtService.class), mock(PasswordEncoder.class));
    private SysUser user;
    private SysMenu menu;

    @BeforeEach
    void setup() {
        user = new SysUser();
        user.setId(71L);
        user.setUsername("login-regression");
        user.setNickname("Park operator");
        user.setStatus(1);
        when(users.selectOne(any())).thenReturn(user);

        menu = new SysMenu();
        menu.setId(91L);
        menu.setParentId(0L);
        menu.setName("Customers");
        menu.setType(2);
        menu.setPath("/crm/customer");
        menu.setPerm("crm:customer:query");
        menu.setStatus(1);
        when(permissions.selectGrantedMenusByUserId(71L)).thenReturn(List.of(menu));
        authenticate("crm:customer:query");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unboundPortalNullRowDoesNotBlockAccessOrChangeGrants() {
        // MyBatis returns a null row when the only selected column is SQL NULL.
        when(users.selectMaps(any())).thenReturn(Collections.singletonList(null));

        Result<AuthController.MyAccess> result = controller.myAccess();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().portalUid()).isNull();
        assertThat(result.getData().name()).isEqualTo("Park operator");
        assertThat(result.getData().admin()).isFalse();
        assertThat(result.getData().menus()).containsExactly(AuthController.GrantedMenu.of(menu));
    }

    @Test
    void missingPortalLookupPreservesAdminAccessAndUsernameFallback() {
        authenticate("ROLE_admin");
        user.setNickname(" ");
        when(users.selectMaps(any())).thenReturn(List.of());

        Result<AuthController.MyAccess> result = controller.myAccess();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().portalUid()).isNull();
        assertThat(result.getData().name()).isEqualTo("login-regression");
        assertThat(result.getData().admin()).isTrue();
        assertThat(result.getData().menus()).isEmpty();
        verify(permissions, never()).selectGrantedMenusByUserId(any());
    }

    @Test
    void boundPortalUidRemainsAvailableWithoutChangingGrants() {
        when(users.selectMaps(any())).thenReturn(List.of(Map.<String, Object>of("portal_uid", " portal-user-71 ")));

        Result<AuthController.MyAccess> result = controller.myAccess();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().portalUid()).isEqualTo("portal-user-71");
        assertThat(result.getData().name()).isEqualTo("Park operator");
        assertThat(result.getData().admin()).isFalse();
        assertThat(result.getData().menus()).containsExactly(AuthController.GrantedMenu.of(menu));
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }
}
