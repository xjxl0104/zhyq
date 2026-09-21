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
    @Mock MktPromoterMapper promoters;
    @Mock MktPromoterService promoterService;
    @Mock BizSettings settings;
    @Test void realLoginRejectsSessionWithoutOpenid() {
        WxSessionClient client = (appId, secret, code) -> new WxSessionClient.Session(null, "session");
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                client, (key, encrypted, iv) -> "13800138000");
        service.setMockLogin(false);
        service.setAppId("id");
        service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("code"))
                .isInstanceOf(BizException.class).hasMessageContaining("微信登录失败");
    }

    @Test void mockLoginPrefixesOpenidWithoutCallingWechat() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> { throw new AssertionError("must not call WeChat"); }, (k, e, i) -> "13800138000");
        service.setMockLogin(true);
        when(promoters.selectOne(any())).thenReturn(null);
        assertThat(service.wxLogin("dev-user").openid()).isEqualTo("mock:dev-user");
    }

    @Test void realLoginRejectsMissingConfiguration() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> new WxSessionClient.Session("o", "s"), (k, e, i) -> "13800138000");
        service.setMockLogin(false);
        assertThatThrownBy(() -> service.wxLogin("code")).isInstanceOf(BizException.class)
                .hasMessageContaining("AppID/Secret");
    }

    @Test void realLoginPropagatesWechatBusinessError() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> { throw new BizException("微信登录失败: invalid code"); }, (k, e, i) -> "13800138000");
        service.setMockLogin(false); service.setAppId("id"); service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("bad")).isInstanceOf(BizException.class).hasMessageContaining("invalid code");
    }

    @Test void decryptFailureDoesNotPersistPhone() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService,
                settings, (a, s, c) -> new WxSessionClient.Session("openid", "session"),
                (k, e, i) -> { throw new BizException("手机号授权解密失败"); });
        service.setMockLogin(false); service.setAppId("id"); service.setAppSecret("secret");
        when(promoters.selectOne(any())).thenReturn(null);
        service.wxLogin("code");
        assertThatThrownBy(() -> service.bindPhone("openid", "encrypted", "iv", null, null)).isInstanceOf(BizException.class);
        verify(promoterService, never()).register(any(), any(), any());
    }

    @Test void duplicatePhoneRaceReturnsStableBindingError() {
        MpAuthService service = new MpAuthService(jwt, promoters, promoterService, settings,
                (a, s, c) -> new WxSessionClient.Session("openid", "session"), (k, e, i) -> "13800138000");
        service.setMockLogin(true);
        com.zhyq.park.marketing.entity.MktPromoter existing = new com.zhyq.park.marketing.entity.MktPromoter();
        existing.setOpenid("other");
        when(promoters.selectOne(any())).thenReturn(null, null, existing);
        doThrow(new BizException("生成邀请码失败,请重试")).when(promoterService).register(any(), any(), any());
        assertThatThrownBy(() -> service.bindPhone("openid", "13800138000", null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("已绑定其他微信");
    }
}
