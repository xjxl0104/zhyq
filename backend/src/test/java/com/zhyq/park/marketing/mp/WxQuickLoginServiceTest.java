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
        wh = new WhAuthService(jwt, warehouses, promoters, audit, provider, (k, e, i) -> "unused", contacts, guard);
        mp.setAppId("app"); mp.setAppSecret("secret");
        wh.setAppId("app"); wh.setAppSecret("secret");
        service = new WxQuickLoginService(mp, wh, promoters, warehouses, contacts, promoterService,
                onboarding, guard, audit, settings, new PasswordAttemptLimiter(), jwt);
    }

    @Test void newWechatPartnerEntersWithoutPhoneAndRecordsAgreement() {
        session();
        doAnswer(call -> { MktPromoter p = call.getArgument(0); p.setId(31L); p.setStatus(1); return p; })
                .when(promoterService).registerWithoutPhone(any(), eq("ABCD2345"));
        when(jwt.issueForIdentity("mp", 31L)).thenReturn("mp-token");
        var result = service.login(MP, request(null), "ip");
        assertThat(result).containsEntry("registered", true).containsEntry("token", "mp-token");
        verify(promoterService).registerWithoutPhone(argThat(p -> "verified-openid".equals(p.getOpenid())
                && p.getPhone() == null && p.getAgreedAt() != null && "v1".equals(p.getAgreementVersion())), eq("ABCD2345"));
        verify(provider, never()).exchangePhone(any(), any(), any());
    }

    @Test void newPartnerNeedsInviteEvenWhenNoPhoneIsRequested() {
        session();
        assertThatThrownBy(() -> service.login(MP,
                new WxQuickLoginService.Request("js-code", "app", true, null, null, null), "ip"))
                .isInstanceOf(BizException.class).hasMessageContaining("邀请码");
        verifyNoInteractions(promoterService, jwt);
    }

    @Test void existingPartnerDoesNotNeedInviteAgain() {
        session();
        when(promoters.selectOne(any())).thenReturn(partner(31L, 1, "verified-openid"));
        assertThat(service.login(MP,
                new WxQuickLoginService.Request("js-code", "app", true, null, null, null), "ip"))
                .containsEntry("registered", true);
        verifyNoInteractions(promoterService);
    }

    @Test void newWechatWarehouseCreatesPendingWorkspaceAndOwnerTogether() {
        session(); createWarehouse();
        when(jwt.issueForIdentity("wh", 41L)).thenReturn("wh-token");
        var result = service.login(WH, request(null), "ip");
        assertThat(result).containsEntry("registered", true).containsEntry("token", "wh-token").containsEntry("warehouseId", 41L);
        MktWarehouse warehouse = (MktWarehouse) result.get("warehouse");
        assertThat(warehouse.getJoinStatus()).isEqualTo(2);
        assertThat(warehouse.getPhone()).isNull();
        assertThat(warehouse.getName()).isEqualTo("待完善云仓");
        verify(contacts).insert(argThat((MktWarehouseContact c) -> c.getWarehouseId().equals(41L)
                && "verified-openid".equals(c.getOpenid()) && "owner".equals(c.getRole()) && c.getStatus() == 1));
        verify(onboarding, never()).passQualification(anyLong(), anyInt());
    }

    @Test void phoneExchangePrecedesRegistrationAndUsesOnlyWechatResponse() {
        session(); when(provider.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000");
        doAnswer(call -> { MktPromoter p = call.getArgument(0); p.setId(31L); p.setStatus(1); return p; })
                .when(promoterService).register(any(), eq("ABCD2345"), eq("mp"));
        service.login(MP, request("phone-code"), "ip");
        var order = inOrder(provider, promoterService);
        order.verify(provider).exchange("app", "secret", "js-code");
        order.verify(provider).exchangePhone("app", "secret", "phone-code");
        order.verify(promoterService).register(argThat(p -> "13800138000".equals(p.getPhone())
                && "verified-openid".equals(p.getOpenid())), eq("ABCD2345"), eq("mp"));
        verify(promoterService, never()).registerWithoutPhone(any(), any());
    }

    @Test void rejectedConsentWrongAppAndPlainPhoneNeverCreateRecords() {
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("code", "app", false, null, null, null), "ip"))
                .hasMessageContaining("同意");
        assertThatThrownBy(() -> service.login(WH, new WxQuickLoginService.Request("code", "wrong", true, null, null, null), "ip"))
                .hasMessageContaining("AppID");
        assertThatThrownBy(() -> service.login(MP, new WxQuickLoginService.Request("code", "app", true, null, "13800138000", null), "ip"))
                .hasMessageContaining("微信手机号授权");
        verifyNoInteractions(provider, promoters, warehouses, contacts, onboarding, promoterService, jwt);
    }

    @Test void phoneProviderFailureDoesNotLeaveEmptyProfiles() {
        session(); when(provider.exchangePhone("app", "secret", "bad-code")).thenThrow(new BizException("授权过期"));
        assertThatThrownBy(() -> service.login(WH, request("bad-code"), "ip")).hasMessageContaining("授权过期");
        verifyNoInteractions(onboarding, promoterService, warehouses, contacts, promoters, jwt);
    }

    @Test void existingWechatOwnerReturnsSameIdentityWithoutChangingContactPhone() {
        session(); MktPromoter p = partner(31L, 1, "verified-openid"); p.setPhone("13900139000");
        when(promoters.selectOne(any())).thenReturn(p);
        service.login(MP, request(null), "ip");
        verify(jwt).issueForIdentity("mp", 31L);
        verifyNoInteractions(promoterService, onboarding, guard);
        assertThat(p.getPhone()).isEqualTo("13900139000");
    }

    @Test void phoneCanBindAdminCreatedPartnerButNotAnotherWechatOwnerOrUnverifiedAccount() {
        session(); when(provider.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000");
        MktPromoter p = partner(31L, 1, null);
        when(promoters.selectOne(any())).thenReturn(null, p);
        when(promoters.update(isNull(), any())).thenReturn(1);
        service.login(MP, request("phone-code"), "ip");
        verify(guard).assertCanBind("mp", 31L); verify(jwt).issueForIdentity("mp", 31L);
        when(promoters.selectOne(any())).thenReturn(null, p);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("其他微信");
        p.setOpenid(null);
        when(promoters.selectOne(any())).thenReturn(null, p);
        doThrow(new BizException("待核验")).when(guard).assertCanBind("mp", 31L);
        assertThatThrownBy(() -> service.login(MP, request("phone-code"), "ip")).hasMessageContaining("待核验");
        verifyNoInteractions(promoterService);
    }

    @Test void warehousePhoneBindingChecksOtherRolesAndSelfReportedAccounts() {
        session(); when(provider.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000");
        when(promoters.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.login(WH, request("phone-code"), "ip")).hasMessageContaining("伙伴身份");
        when(promoters.selectCount(any())).thenReturn(0L);
        MktWarehouse w = warehouse(41L, 2);
        when(warehouses.selectList(any())).thenReturn(List.of(w));
        doThrow(new BizException("待核验")).when(guard).assertCanBind("wh", 41L);
        assertThatThrownBy(() -> service.login(WH, request("phone-code"), "ip")).hasMessageContaining("待核验");
        verifyNoInteractions(onboarding, jwt);
        verify(contacts, never()).insert(any(MktWarehouseContact.class));
    }

    @Test void authorizedPhoneCanBindUnclaimedWarehouseWithoutCreatingAnother() {
        session(); when(provider.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000");
        MktWarehouse w = warehouse(41L, 5);
        when(warehouses.selectList(any())).thenReturn(List.of(w));
        when(warehouses.update(isNull(), any())).thenReturn(1);
        var result = service.login(WH, request("phone-code"), "ip");
        assertThat(result).containsEntry("warehouseId", 41L).containsEntry("registered", true);
        assertThat(w.getContactOpenid()).isEqualTo("verified-openid");
        verify(guard).assertCanBind("wh", 41L);
        verify(contacts).insert(argThat((MktWarehouseContact c) -> "verified-openid".equals(c.getOpenid())
                && "13800138000".equals(c.getPhone()) && Long.valueOf(41L).equals(c.getWarehouseId())));
        verifyNoInteractions(onboarding);
    }

    @Test void explicitMockModeIsTheOnlyWayToUsePlainPhone() {
        wh.setMockLogin(true); createWarehouse();
        service.login(WH, new WxQuickLoginService.Request("dev-user", "development", true, null, "13800138000", null), "ip");
        verify(onboarding).apply(argThat(w -> "mock:dev-user".equals(w.getContactOpenid()) && "13800138000".equals(w.getPhone())));
        verifyNoInteractions(provider);
    }

    @Test void explicitMockModeAcceptsLegacyEmptyAppIdOnBothPortalsWhileProductionRejectsIt() {
        var request = new WxQuickLoginService.Request("dev-user", "", true, null, null, null);
        assertThatThrownBy(() -> service.login(MP, request, "ip")).hasMessageContaining("AppID");
        assertThatThrownBy(() -> service.login(WH, request, "ip")).hasMessageContaining("AppID");
        mp.setMockLogin(true); wh.setMockLogin(true);
        MktPromoter p = partner(31L, 1, "mock:dev-user");
        when(promoters.selectOne(any())).thenReturn(p);
        when(warehouses.selectOne(any())).thenReturn(warehouse(41L, 2));
        assertThat(service.login(MP, request, "ip")).containsEntry("registered", true);
        assertThat(service.login(WH, request, "ip")).containsEntry("registered", true);
        verify(jwt).issueForIdentity("mp", 31L);
        verify(jwt).issueForIdentity("wh", 41L);
        verifyNoInteractions(provider, promoterService, onboarding);
    }

    @Test void frozenExitedAndDisabledContactsCannotLoginOrRegisterAgain() {
        session(); when(promoters.selectOne(any())).thenReturn(partner(31L, 2, "verified-openid"));
        assertThatThrownBy(() -> service.login(MP, request(null), "ip")).hasMessageContaining("冻结");
        MktWarehouseContact contact = new MktWarehouseContact(); contact.setWarehouseId(41L); contact.setStatus(0);
        when(contacts.selectOne(any())).thenReturn(contact);
        assertThatThrownBy(() -> service.login(WH, request(null), "ip")).hasMessageContaining("停用");
        contact.setStatus(1); when(warehouses.selectById(41L)).thenReturn(warehouse(41L, 7));
        assertThatThrownBy(() -> service.login(WH, request(null), "ip")).hasMessageContaining("退出");
        verifyNoInteractions(onboarding, promoterService, jwt);
    }

    @Test void duplicateContactRollsBackWholeRegistrationAndNeverIssuesToken() {
        session(); createWarehouse();
        doThrow(new DuplicateKeyException("openid")).when(contacts).insert(any(MktWarehouseContact.class));
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        WxQuickLoginService transactionalService = (WxQuickLoginService) factory.getProxy();
        assertThatThrownBy(() -> transactionalService.login(WH, request(null), "ip"))
                .isInstanceOf(BizException.class).hasMessageContaining("重新点击登录");
        assertThat(transactions.rolledBack).isTrue(); assertThat(transactions.committed).isFalse();
        verify(onboarding).apply(any()); verifyNoInteractions(jwt);
    }

    @Test void anonymousRequestsAreRateLimitedBeforeCallingWechat() {
        var request = new WxQuickLoginService.Request("code", "app", false, null, null, null);
        for (int i = 0; i < 60; i++) assertThatThrownBy(() -> service.login(MP, request, "same-ip")).hasMessageContaining("同意");
        assertThatThrownBy(() -> service.login(WH, request, "same-ip")).hasMessageContaining("尝试次数过多");
        verifyNoInteractions(provider);
    }

    private void session() { when(provider.exchange("app", "secret", "js-code")).thenReturn(new WxSessionClient.Session("verified-openid", "session-key")); }
    private void createWarehouse() { doAnswer(call -> { MktWarehouse w = call.getArgument(0); w.setId(41L); w.setJoinStatus(1); return w; }).when(onboarding).apply(any()); }
    private static WxQuickLoginService.Request request(String phoneCode) { return new WxQuickLoginService.Request("js-code", "app", true, phoneCode, null, "ABCD2345"); }
    private static MktPromoter partner(Long id, int status, String openid) { MktPromoter p = new MktPromoter(); p.setId(id); p.setStatus(status); p.setOpenid(openid); return p; }
    private static MktWarehouse warehouse(Long id, int status) { MktWarehouse w = new MktWarehouse(); w.setId(id); w.setJoinStatus(status); return w; }
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        boolean committed, rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
