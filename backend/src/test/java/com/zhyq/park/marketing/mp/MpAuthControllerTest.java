package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class MpAuthControllerTest {
    @Test void realModeRejectsPlainPhoneField() {
        MpAuthService auth = Mockito.mock(MpAuthService.class);
        Mockito.when(auth.isMockLogin()).thenReturn(false);
        MpAuthController controller = new MpAuthController(auth);
        assertThatThrownBy(() -> controller.bindPhone(Map.of("openid", "o", "phone", "13800138000")))
                .isInstanceOf(BizException.class).hasMessageContaining("encryptedData");
    }

    @Test void responseDoesNotExposeSessionKey() {
        MpAuthService auth = Mockito.mock(MpAuthService.class);
        Mockito.when(auth.isMockLogin()).thenReturn(true);
        Mockito.when(auth.wxLogin("x")).thenReturn(new MpAuthService.LoginResult(false, null, "mock:x", null));
        Object body = new MpAuthController(auth).wxLogin(Map.of("jsCode", "x")).getData();
        assertThat(body.toString()).doesNotContain("session_key");
    }
}
