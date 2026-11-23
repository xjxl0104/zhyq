package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.zhyq.park.marketing.wh.WhAuthService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;

import static com.zhyq.park.marketing.mp.WxQuickLoginService.Identity.MP;
import static com.zhyq.park.marketing.mp.WxQuickLoginService.Identity.WH;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WxQuickLoginServiceTest {
    @Mock JwtService jwt;
    @Mock MktPromoterMapper promoters;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseContactMapper contacts;
    @Mock MktPromoterService promoterService;
    @Mock MktWarehouseOnboardingService onboarding;
    @Mock MktAuditService audit;
    @Mock BizSettings settings;
    @Mock WxPhoneBindingGuard guard;
    @Mock WxSessionClient provider;
    private WxQuickLoginService service;
    private MpAuthService mp;
    private WhAuthService wh;

    @BeforeAll static void metadata() {
        for (Class<?> type : new Class<?>[]{MktPromoter.class, MktWarehouse.class, MktWarehouseContact.class})
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
    }
    @BeforeEach void setup() {
        mp = new MpAuthService(jwt, promoters, promoterService, settings, provider, (k, e, i) -> "unused", guard);
        wh = spy(new WhAuthService(jwt, warehouses, promoters, audit, provider, (k, e, i) -> "unused", contacts, guard));
        mp.setAppId("app"); mp.setAppSecret("secret");
        wh.setAppId("app"); wh.setAppSecret("secret");
        service = new WxQuickLoginService(mp, wh, promoters, promoterService, guard, audit, new PasswordAttemptLimiter(), jwt);
    }

    @Test void newPartnerNeedsVerifiedPhoneNameAndInviteBeforeAnyRegistration() {
        session();
        assertThatThrownBy(() -> service.login(MP, request(null), "ip")).hasMessageContaining("手机号");
        phone();
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("js-code", "app", true, "phone-code", null, "ABCD2345"), "ip")).hasMessageContaining("姓名");
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("js-code", "app", true, "phone-code", null, null, "测试伙伴"), "ip")).hasMessageContaining("邀请码");
        verifyNoInteractions(promoterService, jwt);
    }
    @Test void phoneExchangePrecedesRegistrationAndPreservesExplicitNameAndAgreement() {
        session(); phone();
        doAnswer(call -> { MktPromoter p = call.getArgument(0); p.setId(31L); p.setStatus(1); return p; })
                .when(promoterService).register(any(), eq("ABCD2345"), eq("mp"));
        when(jwt.issueForIdentity("mp", 31L)).thenReturn("mp-token");
        assertThat(service.login(MP, request("phone-code"), "ip")).containsEntry("token", "mp-token");
        var order = inOrder(provider, promoterService);
        order.verify(provider).exchange("app", "secret", "js-code");
        order.verify(provider).exchangePhone("app", "secret", "phone-code");
        order.verify(promoterService).register(argThat(p -> "13800138000".equals(p.getPhone())
                && "测试伙伴".equals(p.getName()) && "verified-openid".equals(p.getOpenid())
                && p.getAgreedAt() != null && "v1".equals(p.getAgreementVersion())), eq("ABCD2345"), eq("mp"));
        verify(promoterService, never()).registerWithoutPhone(any(), any());
    }
    @Test void completeExistingWechatAccountKeepsLegacyLoginAndNeverOverwritesRealName() {
        session(); MktPromoter p = partner(31L, 1, "verified-openid");
        when(promoters.selectOne(any())).thenReturn(p);
        service.login(MP, new WxQuickLoginService.Request("js-code", "app", true, null, null, null), "ip");
        service.login(MP, request(null), "ip");
        assertThat(p.getName()).isEqualTo("已有伙伴");
        verify(jwt, times(2)).issueForIdentity("mp", 31L);
        verifyNoInteractions(promoterService, guard);
    }
    @Test void differentAuthorizedPhoneCannotSilentlyEnterAnExistingWechatAccount() {
        session(); phone(); MktPromoter p = partner(31L, 1, "verified-openid"); p.setPhone("13900139000");
        when(promoters.selectOne(any())).thenReturn(p);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("不一致");
        verifyNoInteractions(jwt, promoterService);
        verify(promoters, never()).update(any(), any());
    }
    @Test void legacyIncompleteWechatAccountRequiresPhoneAndSafelyCompletesMissingFields() {
        session(); MktPromoter p = partner(31L, 1, "verified-openid"); p.setPhone(null); p.setName("园区伙伴");
        when(promoters.selectOne(any())).thenReturn(p);
        assertThatThrownBy(() -> service.login(MP, request(null), "ip")).hasMessageContaining("手机号");
        phone(); when(promoters.selectOne(any())).thenReturn(p, null);
        when(promoters.update(isNull(), any())).thenReturn(1);
        service.login(MP, request("phone-code"), "ip");
        assertThat(p.getPhone()).isEqualTo("13800138000"); assertThat(p.getName()).isEqualTo("测试伙伴");
        verify(guard).assertCanBind("mp", 31L); verify(jwt).issueForIdentity("mp", 31L);
        verifyNoInteractions(promoterService);
    }
    @Test void incompleteAccountCannotClaimAnotherPartnersPhone() {
        session(); phone(); MktPromoter p = partner(31L, 1, "verified-openid"); p.setPhone(null);
        when(promoters.selectOne(any())).thenReturn(p, partner(32L, 1, "another-openid"));
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("不能自动合并");
        verifyNoInteractions(jwt, promoterService);
        verify(promoters, never()).update(any(), any());
    }
    @Test void concurrentProfileCompletionNeverIssuesTokenWithStaleProfile() {
        session(); phone(); MktPromoter p = partner(31L, 1, "verified-openid"); p.setName("园区伙伴");
        when(promoters.selectOne(any())).thenReturn(p);
        when(promoters.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("资料已变化");
        verifyNoInteractions(jwt);
    }
    @Test void verifiedPhoneBindsUnclaimedPartnerButDoesNotOverwriteItsName() {
        session(); phone(); MktPromoter p = partner(31L, 1, null);
        when(promoters.selectOne(any())).thenReturn(null, p); when(promoters.update(isNull(), any())).thenReturn(1);
        service.login(MP, request("phone-code"), "ip");
        verify(guard).assertCanBind("mp", 31L); verify(jwt).issueForIdentity("mp", 31L);
        assertThat(p.getName()).isEqualTo("已有伙伴"); assertThat(p.getOpenid()).isEqualTo("verified-openid");
    }
    @Test void phoneBindingRejectsAnotherWechatOwnerAndUnverifiedPasswordRegistration() {
        session(); phone(); MktPromoter p = partner(31L, 1, "another-openid");
        when(promoters.selectOne(any())).thenReturn(null, p);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("其他微信");
        p.setOpenid(null); when(promoters.selectOne(any())).thenReturn(null, p);
        doThrow(new BizException("待核验")).when(guard).assertCanBind("mp", 31L);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("待核验");
        verifyNoInteractions(jwt, promoterService);
    }
    @Test void warehouseUsesContactIdentityServiceWithOnlyServerVerifiedValues() {
        session(); phone();
        doReturn(java.util.Map.of("registered", true, "token", "member-token", "warehouseRole", "member"))
                .when(wh).quickLoginIdentity("verified-openid", "13800138000", "测试伙伴");
        assertThat(service.login(WH, request("phone-code"), "ip")).containsEntry("warehouseRole", "member");
        verifyNoInteractions(promoters, warehouses, contacts, onboarding, promoterService, jwt);
    }
    @Test void warehouseIdentityRejectionPropagatesWithoutIssuingSharedWarehouseToken() {
        session(); phone();
        doThrow(new BizException(403, "云仓联系人已停用")).when(wh).quickLoginIdentity(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.login(WH, request("phone-code"), "ip")).hasMessageContaining("停用");
        verifyNoInteractions(jwt, promoterService, onboarding);
    }
    @Test void rejectedConsentWrongAppAndPlainPhoneNeverCreateRecords() {
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("code", "app", false, null, null, null), "ip")).hasMessageContaining("同意");
        assertThatThrownBy(() -> service.login(WH, new WxQuickLoginService.Request("code", "wrong", true, null, null, null), "ip")).hasMessageContaining("AppID");
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("code", "app", true, null, "13800138000", null), "ip")).hasMessageContaining("微信手机号授权");
        verifyNoInteractions(provider, promoters, warehouses, contacts, onboarding, promoterService, jwt);
    }
    @Test void phoneProviderFailureDoesNotLeaveEmptyProfiles() {
        session(); when(provider.exchangePhone("app", "secret", "bad-code")).thenThrow(new BizException("授权过期"));
        assertThatThrownBy(() -> service.login(WH, request("bad-code"), "ip")).hasMessageContaining("授权过期");
        verifyNoInteractions(onboarding, promoterService, warehouses, contacts, promoters, jwt);
    }
    @Test void explicitMockModeAloneAllowsPlainPhoneAndStillRequiresName() {
        mp.setMockLogin(true);
        doAnswer(call -> { MktPromoter p = call.getArgument(0); p.setId(31L); p.setStatus(1); return p; }).when(promoterService).register(any(), eq("ABCD2345"), eq("mp"));
        service.login(MP, new WxQuickLoginService.Request("dev-user", "", true, null, "13800138000", "ABCD2345", "测试伙伴"), "ip");
        verify(promoterService).register(argThat(p -> "mock:dev-user".equals(p.getOpenid())), eq("ABCD2345"), eq("mp"));
        verifyNoInteractions(provider);
    }
    @Test void frozenPartnerCannotLoginOrRegisterAgain() {
        session(); when(promoters.selectOne(any())).thenReturn(partner(31L, 2, "verified-openid"));
        assertThatThrownBy(() -> service.login(MP, request(null), "ip")).hasMessageContaining("冻结");
        verifyNoInteractions(onboarding, promoterService, jwt);
    }
    @Test void duplicateIdentityRollsBackRegistrationAndNeverIssuesToken() {
        session(); phone();
        doThrow(new DuplicateKeyException("identity conflict")).when(promoterService).register(any(), eq("ABCD2345"), eq("mp"));
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        assertThatThrownBy(() -> ((WxQuickLoginService) factory.getProxy()).login(MP, request("phone-code"), "ip"))
                .isInstanceOf(BizException.class).hasMessageContaining("重新点击登录");
        assertThat(transactions.rolledBack).isTrue(); assertThat(transactions.committed).isFalse(); verifyNoInteractions(jwt);
    }
    @Test void anonymousRequestsAreRateLimitedBeforeCallingWechat() {
        var request = new WxQuickLoginService.Request("code", "app", false, null, null, null);
        for (int i = 0; i < 60; i++) assertThatThrownBy(() -> service.login(MP, request, "same-ip")).hasMessageContaining("同意");
        assertThatThrownBy(() -> service.login(WH, request, "same-ip")).hasMessageContaining("尝试次数过多"); verifyNoInteractions(provider);
    }
    private void session() { when(provider.exchange("app", "secret", "js-code")).thenReturn(new WxSessionClient.Session("verified-openid", "session-key")); }
    private void phone() { when(provider.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000"); }
    private static WxQuickLoginService.Request request(String phoneCode) { return new WxQuickLoginService.Request("js-code", "app", true, phoneCode, null, "ABCD2345", "测试伙伴"); }
    private static MktPromoter partner(Long id, int status, String openid) { MktPromoter p = new MktPromoter(); p.setId(id); p.setStatus(status); p.setOpenid(openid); p.setName("已有伙伴"); p.setPhone("13800138000"); return p; }
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        boolean committed, rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
