package com.zhyq.park.marketing.wh;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.mp.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class WhAuthServiceTest {
    WhIdentityService identities=mock(WhIdentityService.class);
    WxSessionClient client=mock(WxSessionClient.class);
    WhAuthService service=new WhAuthService(mock(JwtService.class),mock(MktWarehouseMapper.class),mock(MktPromoterMapper.class),mock(MktAuditService.class),client,(a,b,c)->"unused",mock(MktWarehouseContactMapper.class),mock(WxPhoneBindingGuard.class));
    @BeforeEach void setup(){service.setIdentityService(identities); service.setAppId("app"); service.setAppSecret("secret");}
    @Test void mockLoginUsesIndividualContactAndSanitizesWarehouse(){
        service.setMockLogin(true);var w=new MktWarehouse();w.setId(11L);w.setPhone("13800138000");w.setContactOpenid("sensitive");var c=new MktWarehouseContact();c.setId(5L);
        when(identities.findByOpenid("mock:code",null)).thenReturn(new WhIdentityService.Identity(w,c));when(identities.issueToken(c)).thenReturn("individual-token");
        var r=service.wxLogin("code");assertThat(r.token()).isEqualTo("individual-token");assertThat(r.warehouse().getPhone()).isNull();assertThat(r.warehouse().getContactOpenid()).isNull();verifyNoInteractions(client);
    }
    @Test void realLoginRejectsMissingSessionFields(){when(client.exchange("app","secret","code")).thenReturn(new WxSessionClient.Session("openid",null));assertThatThrownBy(()->service.wxLogin("code")).isInstanceOf(BizException.class).hasMessageContaining("openid/session_key");verifyNoInteractions(identities);}
    @Test void mismatchedAppIdFailsBeforeWechatCall(){assertThatThrownBy(()->service.wxLogin(null,"code","wrong-app")).isInstanceOf(BizException.class).hasMessageContaining("AppID");verifyNoInteractions(client,identities);}
    @Test void modernPhoneBindingRequiresOriginalOneTimeTicketAndForwardsVerifiedPhoneAndName(){
        when(client.exchange("app","secret","code")).thenReturn(new WxSessionClient.Session("openid","session"));when(client.exchangePhone("app","secret","phone-code")).thenReturn("13800138000");
        String ticket=service.wxLogin("code").loginTicket();
        assertThatThrownBy(()->service.bindPhoneAuthorized("forged",ticket,"phone-code",null,null,"张三")).isInstanceOf(BizException.class);
        service.bindPhoneAuthorized("openid",ticket,"phone-code",null,null,"张三");verify(identities).bindVerifiedPhone("openid","13800138000","张三");
        assertThatThrownBy(()->service.bindPhoneAuthorized("openid",ticket,"phone-code",null,null,"张三")).isInstanceOf(BizException.class);
    }
    @Test void realModeRejectsPlaintextPhoneBeforeIdentity(){assertThatThrownBy(()->service.bindPhoneWithName("openid","13800138000","张三")).isInstanceOf(BizException.class);verifyNoInteractions(identities);}
}
