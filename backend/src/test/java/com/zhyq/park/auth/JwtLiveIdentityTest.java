package com.zhyq.park.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.auth.mapper.AuthQueryMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtLiveIdentityTest {
    private static final String SECRET = "live-identity-test-secret-more-than-32-bytes";
    SysUserMapper users = mock(SysUserMapper.class);
    AuthQueryMapper permissions = mock(AuthQueryMapper.class);
    MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    MktCredentialMapper credentials = mock(MktCredentialMapper.class);
    JwtAccountService accounts = new JwtAccountService(users, permissions, promoters, warehouses, credentials);
    JwtService jwt = new JwtService(SECRET, 3600, accounts);
    SysUser user;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MktCredential.class);
        user = new SysUser(); user.setId(1L); user.setUsername("ops"); user.setStatus(1); user.setPassword("hash-before-reset");
        when(users.selectById(1L)).thenReturn(user);
        when(permissions.selectRoleCodesByUserId(1L)).thenReturn(List.of("admin"));
        when(permissions.selectPermsByUserId(1L)).thenReturn(List.of("system:user:edit"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void disablingAnAccountImmediatelyInvalidatesAnAlreadyIssuedJwt() throws Exception {
        String token = jwt.issueForIdentity("admin", 1L);
        assertThat(authenticate(token, "/system/user/page")).isNotNull();
        user.setStatus(0);
        assertThat(authenticate(token, "/system/user/page")).isNull();
        assertThatThrownBy(() -> jwt.issueForIdentity("admin", 1L)).isInstanceOf(BizException.class);
    }

    @Test void revokedRolesAndPermissionsAreNotRecoveredFromOldJwtClaims() throws Exception {
        String token = jwt.issueForIdentity("admin", 1L);
        when(permissions.selectRoleCodesByUserId(1L)).thenReturn(List.of());
        when(permissions.selectPermsByUserId(1L)).thenReturn(List.of());
        assertThat(authenticate(token, "/system/user/page").getAuthorities()).isEmpty();
    }

    @Test void passwordResetInvalidatesPreviousJwtWithoutExposingHashInClaims() throws Exception {
        String token = jwt.issueForIdentity("admin", 1L);
        assertThat(jwt.parse(token).toString()).doesNotContain("hash-before-reset");
        assertThat(jwt.parse(token).get("subjectType")).isEqualTo("admin");
        user.setPassword("hash-after-reset");
        assertThat(authenticate(token, "/system/user/page")).isNull();
        assertThat(authenticate(jwt.issueForIdentity("admin", 1L), "/system/user/page")).isNotNull();
    }

    @Test void backendUsernameCannotBecomePortalIdentity() throws Exception {
        user.setUsername("wh:11");
        assertThatThrownBy(() -> jwt.issueForIdentity("admin", 1L)).isInstanceOf(BizException.class);
        // A signed legacy subject plus a role claim is insufficient to select another identity namespace.
        String legacy = Jwts.builder().subject("wh:11").claim("uid", 1).claim("auth", List.of("ROLE_WH"))
                .expiration(new Date(System.currentTimeMillis() + 60000)).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(authenticate(legacy, "/wh/v1/apply")).isNull();
    }

    @Test void portalStatusAndCredentialChangesInvalidateExistingSessions() throws Exception {
        MktPromoter partner = new MktPromoter(); partner.setId(7L); partner.setStatus(1);
        when(promoters.selectById(7L)).thenReturn(partner);
        String token = jwt.issueForIdentity("mp", 7L);
        assertThat(authenticate(token, "/mp/v1/me")).isNotNull();
        partner.setStatus(2);
        assertThat(authenticate(token, "/mp/v1/me")).isNull();
        assertThatThrownBy(() -> jwt.issueForIdentity("mp", 7L)).isInstanceOf(BizException.class);
        partner.setStatus(1);
        MktCredential password = new MktCredential(); password.setId(15L); password.setStatus(1);
        password.setUsername("partner"); password.setPasswordHash("new-password-hash");
        when(credentials.selectOne(any())).thenReturn(password);
        assertThat(authenticate(token, "/mp/v1/me")).isNull(); // first password setup also replaces existing sessions
        String configuredToken = jwt.issueForIdentity("mp", 7L);
        assertThat(authenticate(configuredToken, "/mp/v1/me")).isNotNull();
        password.setPasswordHash("changed-password-hash");
        assertThat(authenticate(configuredToken, "/mp/v1/me")).isNull();
        password.setStatus(0);
        assertThatThrownBy(() -> jwt.issueForIdentity("mp", 7L)).isInstanceOf(BizException.class);
    }

    @Test void warehouseTokenCannotCrossRolePathsAndExitedWarehouseIsRejected() throws Exception {
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(11L); warehouse.setJoinStatus(5);
        when(warehouses.selectById(11L)).thenReturn(warehouse);
        String token = jwt.issueForIdentity("wh", 11L);
        assertThat(authenticate(token, "/wh/v1/apply").getName()).isEqualTo("wh:11");
        assertThat(authenticate(token, "/mp/v1/me")).isNull();
        assertThat(authenticate(token, "/crm/customer/page")).isNull();
        assertThat(authenticate(token, "/other/wh/v1/apply")).isNull();
        warehouse.setJoinStatus(7);
        assertThat(authenticate(token, "/wh/v1/apply")).isNull();
    }

    private Authentication authenticate(String token, String path) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api" + path);
        request.setContextPath("/api"); request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthFilter(jwt).doFilter(request, new MockHttpServletResponse(), (r, s) -> {});
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
