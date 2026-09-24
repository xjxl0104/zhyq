package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class WxPhoneBindingGuardTest {
    @Test void passwordRegistrationCannotBeMergedUsingAnUnverifiedPhone() {
        MktCredentialMapper mapper = mock(MktCredentialMapper.class);
        MktCredential credential = new MktCredential();
        credential.setRegistrationPhone("13800138000");
        when(mapper.selectOne(any())).thenReturn(credential);
        assertThatThrownBy(() -> new WxPhoneBindingGuard(mapper).assertCanBind("mp", 1L))
                .isInstanceOf(BizException.class).hasMessageContaining("待核验");
    }
    @Test void passwordAddedByAnAlreadyVerifiedIdentityDoesNotBlockBinding() {
        MktCredentialMapper mapper = mock(MktCredentialMapper.class);
        when(mapper.selectOne(any())).thenReturn(new MktCredential());
        assertThatCode(() -> new WxPhoneBindingGuard(mapper).assertCanBind("wh", 1L)).doesNotThrowAnyException();
    }
}
