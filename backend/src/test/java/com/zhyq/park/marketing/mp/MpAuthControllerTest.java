package com.zhyq.park.marketing.mp;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MpAuthControllerTest {
    @Test void realModeNeverPassesUnverifiedPlainPhoneIntoBinding() {
        MpAuthService auth = Mockito.mock(MpAuthService.class);
        Mockito.when(auth.isMockLogin()).thenReturn(false);
        Mockito.when(auth.bindPhoneAuthorized("o", null, null, null, null, null, null))
                .thenReturn(new MpAuthService.LoginResult(false, null, "o", null));
        new MpAuthController(auth).bindPhone(Map.of("openid", "o", "phone", "13800138000"));
        Mockito.verify(auth).bindPhoneAuthorized("o", null, null, null, null, null, null);
    }

    @Test void responseDoesNotExposeSessionKey() {
        MpAuthService auth = Mockito.mock(MpAuthService.class);
        Mockito.when(auth.isMockLogin()).thenReturn(true);
        Mockito.when(auth.wxLogin("x", null)).thenReturn(new MpAuthService.LoginResult(false, null, "mock:x", null));
        Object body = new MpAuthController(auth).wxLogin(Map.of("jsCode", "x")).getData();
        assertThat(body.toString()).doesNotContain("session_key");
    }
}
