package com.zhyq.park.marketing.password;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.zhyq.park.marketing.password.PasswordAuthService.Identity.MP;
import static com.zhyq.park.marketing.password.PasswordAuthService.Identity.WH;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordAuthServiceTest {
    @Mock MktCredentialMapper credentials;
    @Mock MktPromoterMapper promoters;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseContactMapper contacts;
    @Mock MktPromoterService promoterService;
    @Mock MktWarehouseOnboardingService warehouseService;
    @Mock MktAuditService auditService;
    @Mock BizSettings settings;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private JwtService jwt;
    private PasswordAuthService service;

    @BeforeEach void setup() {
        jwt = new JwtService("test-secret-for-password-tests-32-bytes-long", 3600,
                new com.zhyq.park.auth.JwtAccountService(mock(com.zhyq.park.system.mapper.SysUserMapper.class),
                        mock(com.zhyq.park.auth.mapper.AuthQueryMapper.class), promoters, warehouses, credentials));
        service = new PasswordAuthService(credentials, promoters, warehouses, contacts, promoterService,
                warehouseService, auditService, settings, encoder, jwt, new PasswordAttemptLimiter());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MktCredential.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MktPromoter.class);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void partnerRegistrationCreatesBusinessProfileAndBcryptCredential() {
        when(settings.getInt("marketing", "invite_grace_days", 7)).thenReturn(7);
        when(promoterService.register(any(), isNull(), eq("mp"))).thenAnswer(invocation -> {
            MktPromoter p = invocation.getArgument(0); p.setId(31L); p.setStatus(1);
            when(promoters.selectById(31L)).thenReturn(p); return p;
        });
        Map<String, Object> result = service.register(MP, registration(), "127.0.0.1");
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials).insert(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt-sha256}$2").isNotEqualTo("Passw0rd123");
        when(credentials.selectOne(any())).thenReturn(saved.getValue());
        assertThat(service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "Passw0rd123"), "ip")).containsKey("token");
        assertThat(saved.getValue().getUsername()).isEqualTo("demo_user");
        assertThat(saved.getValue().getIdentityType()).isEqualTo("mp");
        assertThat(saved.getValue().getIdentityId()).isEqualTo(31L);
        ArgumentCaptor<MktPromoter> profile = ArgumentCaptor.forClass(MktPromoter.class);
        verify(promoterService).register(profile.capture(), isNull(), eq("mp"));
        assertThat(profile.getValue().getOpenid()).isNull();
        assertThat(profile.getValue().getIdVerified()).isZero();
        assertThat(profile.getValue().getAgreedAt()).isNotNull();
        assertThat(profile.getValue().getInviteDeadline()).isNotNull();
        assertThat(jwt.parse((String) result.get("token")).getSubject()).isEqualTo("mp:31");
        assertThat(result).doesNotContainKeys("password", "passwordHash", "registrationPhone");
    }

    @Test void warehouseRegistrationUsesExistingApplicationWorkflow() {
        when(warehouseService.apply(any())).thenAnswer(invocation -> {
            MktWarehouse w = invocation.getArgument(0); w.setId(41L); w.setJoinStatus(2);
            when(warehouses.selectById(41L)).thenReturn(w); return w;
        });
        Map<String, Object> result = service.register(WH, registration(), "127.0.0.1");
        ArgumentCaptor<MktWarehouse> profile = ArgumentCaptor.forClass(MktWarehouse.class);
        verify(warehouseService).apply(profile.capture());
        assertThat(profile.getValue().getCode()).startsWith("WH-").hasSize(23);
        assertThat(profile.getValue().getContactOpenid()).isNull();
        assertThat(profile.getValue().getRemark()).contains("未经手机验证");
        assertThat(((Map<?, ?>) result.get("warehouse")).get("joinStatus")).isEqualTo(2);
        assertThat(jwt.parse((String) result.get("token")).getSubject()).isEqualTo("wh:41");
        assertThat(jwt.parse((String) result.get("token")).get("auth")).isEqualTo(List.of("ROLE_WH"));
    }

    @Test void unverifiedPhoneCannotClaimExistingPartner() {
        when(promoters.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.register(MP, registration(), "ip")).isInstanceOf(BizException.class).hasMessageContaining("不能凭填写手机号合并身份");
        verifyNoInteractions(promoterService, warehouseService);
        verify(credentials, never()).insert(any(MktCredential.class));
    }

    @Test void unverifiedPhoneCannotClaimExistingWarehouseContact() {
        when(contacts.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.register(WH, registration(), "ip")).isInstanceOf(BizException.class).hasMessageContaining("已有业务档案");
        verifyNoInteractions(warehouseService);
    }

    @Test void registrationRequiresConsentAndNonEmptyCredentialsBeforeCreatingRecords() {
        var request = registration();
        assertThatThrownBy(() -> service.register(MP, new PasswordAuthService.RegisterRequest(request.username(), request.password(), request.phone(), request.name(), null, null, false), "ip"))
                .hasMessageContaining("同意");
        assertThatThrownBy(() -> service.register(MP, new PasswordAuthService.RegisterRequest(request.username(), "", request.phone(), request.name(), null, null, true), "ip"))
                .hasMessageContaining("请输入密码");
        assertThatThrownBy(() -> service.register(MP, new PasswordAuthService.RegisterRequest("  ", "123", request.phone(), request.name(), null, null, true), "ip"))
                .hasMessageContaining("请输入账号");
        verifyNoInteractions(credentials, promoterService, warehouseService);
    }

    @ParameterizedTest
    @EnumSource(PasswordAuthService.Identity.class)
    void bothIdentitiesCanRegisterAndLoginWith123(PasswordAuthService.Identity identity) {
        if (identity == MP) {
            when(settings.getInt("marketing", "invite_grace_days", 7)).thenReturn(7);
            when(promoterService.register(any(), isNull(), eq("mp"))).thenAnswer(invocation -> {
                MktPromoter p = invocation.getArgument(0); p.setId(31L); p.setStatus(1);
                when(promoters.selectById(31L)).thenReturn(p); return p;
            });
        } else {
            when(warehouseService.apply(any())).thenAnswer(invocation -> {
                MktWarehouse w = invocation.getArgument(0); w.setId(31L); w.setJoinStatus(2);
                when(warehouses.selectById(31L)).thenReturn(w); return w;
            });
        }
        var request = new PasswordAuthService.RegisterRequest("123", "123", "13800138000", "新用户", null, "示例云仓", true);
        assertThat(service.register(identity, request, "ip")).containsKey("token");
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials).insert(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("123");
        when(credentials.selectOne(any())).thenReturn(saved.getValue());
        assertThat(service.login(identity, new PasswordAuthService.LoginRequest("123", "123"), "ip")).containsKey("token");
        assertThatThrownBy(() -> service.register(identity, request, "ip")).hasMessageContaining("已注册");
    }

    @ParameterizedTest
    @EnumSource(PasswordAuthService.Identity.class)
    void bothIdentitiesCanSetupAndChangeShortPasswordToFullLongPassword(PasswordAuthService.Identity identity) {
        authenticate(identity.code + ":31", identity.role);
        if (identity == MP) {
            when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
            when(promoters.selectById(31L)).thenReturn(activePromoter());
        } else {
            MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(31L); warehouse.setJoinStatus(2);
            when(warehouses.selectById(31L)).thenReturn(warehouse);
        }
        service.setup(identity, new PasswordAuthService.SetupRequest("123", "123", null), "ip");
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials).insert(saved.capture());
        MktCredential credential = saved.getValue(); credential.setId(2L);
        when(credentials.selectOne(any())).thenReturn(credential);
        assertThat(service.login(identity, new PasswordAuthService.LoginRequest("123", "123"), "ip")).containsKey("token");

        String longPassword = "密码🔑".repeat(300) + "末尾";
        when(credentials.update(isNull(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MktCredential> update = invocation.getArgument(1);
            update.getSqlSegment();
            String hash = update.getParamNameValuePairs().values().stream()
                    .filter(v -> v instanceof String text && text.startsWith("{bcrypt-sha256}$2") && !text.equals(credential.getPasswordHash()))
                    .map(Object::toString).findFirst().orElseThrow();
            credential.setPasswordHash(hash);
            return 1;
        });
        service.setup(identity, new PasswordAuthService.SetupRequest("园区 伙伴@123", longPassword, "123"), "ip");
        assertThat(service.login(identity, new PasswordAuthService.LoginRequest("园区 伙伴@123", longPassword), "ip")).containsKey("token");
        assertThatThrownBy(() -> service.login(identity, new PasswordAuthService.LoginRequest("园区 伙伴@123", longPassword + "变"), "ip"))
                .hasMessage("账号或密码错误");
        assertThatThrownBy(() -> service.setup(identity, new PasswordAuthService.SetupRequest("123", "1", longPassword + "变"), "ip"))
                .hasMessage("当前密码不正确");
        // The full long password also works as the current password on the next change.
        service.setup(identity, new PasswordAuthService.SetupRequest("123", "1", longPassword), "ip");
        assertThat(service.login(identity, new PasswordAuthService.LoginRequest("123", "1"), "ip")).containsKey("token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "abcdefgh", "中文🔑!", " "})
    void passwordHasNoCharacterCombinationRequirementAndWhitespaceIsPreserved(String password) {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        when(promoters.selectById(31L)).thenReturn(activePromoter());
        service.setup(MP, new PasswordAuthService.SetupRequest("伙伴@1", password, null), "ip");
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials).insert(saved.capture());
        when(credentials.selectOne(any())).thenReturn(saved.getValue());
        assertThat(service.login(MP, new PasswordAuthService.LoginRequest("伙伴@1", password), "ip")).containsKey("token");
    }

    @Test void usernameStorageChecksNormalizedUnicodeCodePointsWithoutTruncation() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        for (String username : List.of("a".repeat(512), "😀".repeat(512), "İ".repeat(256))) {
            service.setup(MP, new PasswordAuthService.SetupRequest(username, "123", null), "ip");
        }
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials, times(3)).insert(saved.capture());
        assertThat(saved.getAllValues()).extracting(MktCredential::getUsername)
                .containsExactly("a".repeat(512), "😀".repeat(512), "i\u0307".repeat(256));
        for (String username : List.of("a".repeat(513), "😀".repeat(513), "İ".repeat(257))) {
            assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest(username, "123", null), "ip"))
                    .hasMessageContaining("存储容量");
        }
        verify(credentials, times(3)).insert(any(MktCredential.class));
    }

    @Test void legacyBcryptDoesNotAcceptTruncatedLongCandidatesOrThrowEncodingErrors() {
        MktCredential credential = credential(); credential.setPasswordHash(encoder.encode("a".repeat(72)));
        when(credentials.selectOne(any())).thenReturn(credential);
        when(promoters.selectById(31L)).thenReturn(activePromoter());
        assertThat(service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "a".repeat(72)), "ip")).containsKey("token");
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "a".repeat(72) + "尾"), "ip"))
                .isInstanceOf(BizException.class).hasMessage("账号或密码错误");
    }

    @Test void unknownUserWithLongPasswordReturnsNormalLoginError() {
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("无此用户", "密".repeat(300)), "ip"))
                .isInstanceOf(BizException.class).hasMessage("账号或密码错误");
    }

    @Test void loginScopesUsernameLookupToRequestedIdentityAndNormalizesUsername() {
        MktCredential credential = credential();
        when(credentials.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<MktCredential> q = invocation.getArgument(0);
            q.getSqlSegment();
            assertThat(q.getParamNameValuePairs().values()).contains("wh");
            assertThat(q.getParamNameValuePairs().values()).containsAnyOf("demo_user", 31L);
            return credential;
        });
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(31L); warehouse.setJoinStatus(2);
        when(warehouses.selectById(31L)).thenReturn(warehouse);
        Map<String, Object> result = service.login(WH, new PasswordAuthService.LoginRequest(" DEMO_USER ", "Passw0rd123"), "ip");
        assertThat(jwt.parse((String) result.get("token")).getSubject()).isEqualTo("wh:31");
        verifyNoInteractions(promoters);
    }

    @Test void wrongAndUnknownCredentialsHaveSameErrorAndAreRateLimited() {
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("unknown", "wrong123"), "ip"))
                .isInstanceOf(BizException.class).hasMessage("账号或密码错误");
        when(credentials.selectOne(any())).thenReturn(credential());
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "wrong123"), "ip"))
                    .isInstanceOf(BizException.class).hasMessage("账号或密码错误");
        }
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "wrong123"), "other-ip"))
                .hasMessageContaining("尝试次数过多");
        verifyNoInteractions(promoters, warehouses);
    }

    @Test void unicodeAliasesResolvedByDatabaseShareOneAccountAttemptLimitAcrossAddresses() {
        when(credentials.selectOne(any())).thenReturn(credential());
        for (int i = 0; i < 10; i++) {
            String username = i % 2 == 0 ? "123" : "１２３";
            assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest(username, "wrong"), "ip-" + username))
                    .hasMessage("账号或密码错误");
        }
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("１２３", "wrong"), "another-ip"))
                .hasMessageContaining("尝试次数过多");
        verifyNoInteractions(promoters, warehouses);
    }

    @Test void frozenBusinessCannotLoginEvenWithValidPassword() {
        when(credentials.selectOne(any())).thenReturn(credential());
        MktPromoter p = new MktPromoter(); p.setId(31L); p.setStatus(2);
        when(promoters.selectById(31L)).thenReturn(p);
        assertThatThrownBy(() -> service.login(MP, new PasswordAuthService.LoginRequest("demo_user", "Passw0rd123"), "ip"))
                .hasMessageContaining("冻结");
    }

    @Test void setupRejectsAnonymousAndOtherIdentityWithoutReadingCredentials() {
        var request = new PasswordAuthService.SetupRequest("demo_user", "Passw0rd123", null);
        assertThatThrownBy(() -> service.setup(MP, request, "ip")).hasMessageContaining("请先登录");
        authenticate("wh:31", "ROLE_WH");
        assertThatThrownBy(() -> service.setup(MP, request, "ip")).hasMessageContaining("请先登录");
        authenticate("mp:31", "ROLE_WH");
        assertThatThrownBy(() -> service.setup(MP, request, "ip")).hasMessageContaining("请先登录");
        verifyNoInteractions(credentials, promoters, warehouses);
    }

    @Test void existingWechatIdentityCanSetPasswordWithoutCreatingNewProfile() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "Passw0rd123", null), "ip");
        ArgumentCaptor<MktCredential> saved = ArgumentCaptor.forClass(MktCredential.class);
        verify(credentials).insert(saved.capture());
        assertThat(saved.getValue().getIdentityId()).isEqualTo(31L);
        assertThat(saved.getValue().getRegistrationPhone()).isNull();
        verifyNoInteractions(promoterService, warehouseService);
    }

    @Test void credentialChangesRequireCurrentPassword() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        when(credentials.selectOne(any())).thenReturn(credential());
        assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "NewPass123", null), "ip"))
                .hasMessageContaining("请填写当前密码");
        assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "NewPass123", "wrong123"), "ip"))
                .hasMessageContaining("当前密码不正确");
        verify(credentials, never()).update(any(), any());
    }

    @Test void credentialChangeUsesAtomicPasswordComparisonAndHandlesConflict() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        MktCredential credential = credential();
        when(credentials.selectOne(any())).thenReturn(credential);
        when(credentials.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "NewPass123", "Passw0rd123"), "ip"))
                .hasMessageContaining("账号已更新");
    }

    @Test void currentPasswordAllowsChangingUsernameAndPasswordForSameIdentity() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        MktCredential credential = credential();
        AtomicReference<String> updatedHash = new AtomicReference<>();
        when(credentials.selectOne(any())).thenReturn(credential, null);
        when(credentials.update(isNull(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MktCredential> update = invocation.getArgument(1);
            update.getSqlSegment();
            assertThat(update.getParamNameValuePairs().values()).contains(credential.getId(), credential.getPasswordHash(), "new_user");
            String newHash = update.getParamNameValuePairs().values().stream().filter(v -> v instanceof String text
                    && text.startsWith("{bcrypt-sha256}$2")).map(Object::toString).findFirst().orElseThrow();
            updatedHash.set(newHash);
            return 1;
        });
        assertThatCode(() -> service.setup(MP, new PasswordAuthService.SetupRequest("new_user", "NewPass123", "Passw0rd123"), "ip"))
                .doesNotThrowAnyException();
        verify(credentials, never()).insert(any(MktCredential.class));
        verify(auditService).log("password.setup", "promoter", 31L, "设置账号密码");
        credential.setPasswordHash(updatedHash.get());
        when(credentials.selectOne(any())).thenReturn(credential);
        when(promoters.selectById(31L)).thenReturn(activePromoter());
        assertThat(service.login(MP, new PasswordAuthService.LoginRequest("new_user", "NewPass123"), "ip")).containsKey("token");
    }

    @Test void credentialUniquenessRaceReturnsUsefulConflictInsteadOfSqlDetails() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(activePromoter());
        when(credentials.insert(any(MktCredential.class))).thenThrow(new DuplicateKeyException("secret-sql"));
        assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "Passw0rd123", null), "ip"))
                .hasMessageContaining("已注册").hasMessageNotContaining("secret-sql");
    }

    @Test void credentialAndRequestSerializationNeverIncludesPassword() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(credential())).doesNotContain("passwordHash", "registrationPhone", "$2");
        assertThat(mapper.writeValueAsString(new PasswordAuthService.SetupRequest("demo_user", "Secret123", "OldSecret123")))
                .doesNotContain("Secret123", "password", "currentPassword");
    }

    @Test void partnerDeletedAfterAuthenticationCannotCreateNewLoginCredential() {
        authenticate("mp:31", "ROLE_MP");
        when(promoters.selectForUpdate(31L)).thenReturn(null);
        assertThatThrownBy(() -> service.setup(MP, new PasswordAuthService.SetupRequest("demo_user", "Passw0rd123", null), "ip"))
                .hasMessageContaining("账号不存在");
        verifyNoInteractions(credentials, auditService);
    }

    private MktCredential credential() {
        MktCredential c = new MktCredential(); c.setId(2L); c.setIdentityType("mp"); c.setIdentityId(31L);
        c.setUsername("demo_user"); c.setPasswordHash(encoder.encode("Passw0rd123")); c.setStatus(1); return c;
    }
    private MktPromoter activePromoter() {
        MktPromoter p = new MktPromoter(); p.setId(31L); p.setStatus(1); return p;
    }
    private PasswordAuthService.RegisterRequest registration() {
        return new PasswordAuthService.RegisterRequest("Demo_User", "Passw0rd123", "13800138000", "新用户", null, "示例云仓", true);
    }
    private void authenticate(String subject, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
