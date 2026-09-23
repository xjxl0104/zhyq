package com.zhyq.park.marketing.mp;

import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.MktPromoterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MpAuthServiceTest {
    @Mock JwtService jwt;
    @Mock com.zhyq.park.marketing.mp.WxPhoneBindingGuard phoneBindingGuard;
    @Mock MktPromoterMapper promoters;
    @Mock MktPromoterService promoterService;
    @Mock BizSettings settings;
    @Test void realLoginRejectsSessionWithoutOpenid() {
        WxSessionClient client = (appId, secret, code) -> new WxSessionClient.Session(null, "session");
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                client, (key, encrypted, iv) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(false);
        service.setAppId("id");
        service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("code"))
                .isInstanceOf(BizException.class).hasMessageContaining("微信登录失败");
    }

    @Test void mockLoginPrefixesOpenidWithoutCallingWechat() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> { throw new AssertionError("must not call WeChat"); }, (k, e, i) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(true);
        when(promoters.selectOne(any())).thenReturn(null);
        assertThat(service.wxLogin("dev-user").openid()).isEqualTo("mock:dev-user");
    }

    @Test void realLoginRejectsMissingConfiguration() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> new WxSessionClient.Session("o", "s"), (k, e, i) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(false);
        assertThatThrownBy(() -> service.wxLogin("code")).isInstanceOf(BizException.class)
                .hasMessageContaining("AppID/Secret");
    }

    @Test void realLoginPropagatesWechatBusinessError() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> { throw new BizException("微信登录失败: invalid code"); }, (k, e, i) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("id"); service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("bad")).isInstanceOf(BizException.class).hasMessageContaining("invalid code");
    }

    @Test void decryptFailureDoesNotPersistPhone() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService,
                settings, (a, s, c) -> new WxSessionClient.Session("openid", "session"),
                (k, e, i) -> { throw new BizException("手机号授权解密失败"); }, phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("id"); service.setAppSecret("secret");
        when(promoters.selectOne(any())).thenReturn(null);
        String ticket = service.wxLogin("code").loginTicket();
        assertThatThrownBy(() -> service.bindPhoneAuthorized("openid", ticket, null, "encrypted", "iv", null, null)).isInstanceOf(BizException.class);
        verify(promoterService, never()).register(any(), any(), any());
    }

    @Test void duplicatePhoneRaceReturnsStableBindingError() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> new WxSessionClient.Session("openid", "session"), (k, e, i) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(true);
        com.zhyq.park.marketing.entity.MktPromoter existing = new com.zhyq.park.marketing.entity.MktPromoter();
        existing.setOpenid("other");
        when(promoters.selectOne(any())).thenReturn(null, null, existing);
        doThrow(new BizException("生成邀请码失败,请重试")).when(promoterService).register(any(), any(), any());
        assertThatThrownBy(() -> service.bindPhone("openid", "13800138000", null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("已绑定其他微信");
    }
    @Test void mismatchedAppIdFailsBeforeWechatCall() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> { throw new AssertionError("wrong app must not be called"); }, (k, e, i) -> "13800138000", phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("partner-app"); service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("code", "warehouse-app"))
                .isInstanceOf(BizException.class).hasMessageContaining("AppID");
    }

    @Test void modernPhoneBindingRequiresOriginalTicketAndRejectsReplay() {
        WxSessionClient client = org.mockito.Mockito.mock(WxSessionClient.class);
        when(client.exchange("id", "secret", "code")).thenReturn(new WxSessionClient.Session("openid", "key"));
        when(client.exchangePhone("id", "secret", "phone-code")).thenReturn("13800138000");
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings, client, (k, e, i) -> "unused", phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("id"); service.setAppSecret("secret");
        String ticket = service.wxLogin("code", "id").loginTicket();
        assertThatThrownBy(() -> service.bindPhoneAuthorized("someone-else", ticket, "phone-code", null, null, null, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.bindPhoneAuthorized("openid", null, "phone-code", null, null, null, null))
                .isInstanceOf(BizException.class);
        var result = service.bindPhoneAuthorized("openid", ticket, "phone-code", null, null, null, "伙伴");
        assertThat(result.registered()).isTrue();
        verify(promoterService).register(org.mockito.ArgumentMatchers.argThat(p -> "13800138000".equals(p.getPhone())),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("mp"));
        assertThatThrownBy(() -> service.bindPhoneAuthorized("openid", ticket, "phone-code", null, null, null, null))
                .isInstanceOf(BizException.class);
        verify(client).exchangePhone("id", "secret", "phone-code");
    }

    @Test void realModeRejectsPlainPhone() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> null, (k, e, i) -> "unused", phoneBindingGuard);
        service.setMockLogin(false);
        assertThatThrownBy(() -> service.bindPhone("forged", "13800138000", null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("微信手机号授权");
    }
    @Test void unverifiedPasswordAccountCannotBeClaimedByPhoneBinding() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> null, (k, e, i) -> "unused", phoneBindingGuard);
        service.setMockLogin(true);
        var existing = new com.zhyq.park.marketing.entity.MktPromoter();
        existing.setId(9L); existing.setPhone("13800138000");
        when(promoters.selectOne(any())).thenReturn(null, existing);
        doThrow(new BizException("待核验")).when(phoneBindingGuard).assertCanBind("mp", 9L);
        assertThatThrownBy(() -> service.bindPhone("openid", "13800138000", null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("待核验");
        verify(promoters, never()).update(any(), any());
        verify(promoterService, never()).register(any(), any(), any());
    }
}
