package com.zhyq.park.marketing.wh;

import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.mp.WxSessionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhAuthServiceTest {
    @org.junit.jupiter.api.BeforeAll static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), MktWarehouse.class);
    }
    @Mock JwtService jwt;
    @Mock com.zhyq.park.marketing.mp.WxPhoneBindingGuard phoneBindingGuard;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktPromoterMapper promoters;
    @Mock MktAuditService audit;
    @Mock MktWarehouseContactMapper contacts;

    @Test void mockLoginIssuesWarehouseSubjectAndKeepsUnboundSeparate() {
        MktWarehouse w = warehouse(11L, "mock:wx-11", null, "13800138000");
        when(warehouses.selectOne(any())).thenReturn(w);
        when(jwt.issueForIdentity("wh", 11L)).thenReturn("wh-token");
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c) -> { throw new AssertionError("mock mode must not call WeChat"); },
                (key, encrypted, iv) -> "13800138000", contacts, phoneBindingGuard);
        service.setMockLogin(true);
        WhAuthService.LoginResult r = service.wxLogin("wx-11");
        assertThat(r.registered()).isTrue();
        assertThat(r.token()).isEqualTo("wh-token");
        verify(jwt).issueForIdentity("wh", 11L);
    }

    @Test void realLoginRejectsMissingSessionFields() {
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c) -> new WxSessionClient.Session("openid", null),
                (key, encrypted, iv) -> "13800138000", contacts, phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("app"); service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin("code"))
                .isInstanceOf(BizException.class).hasMessageContaining("openid/session_key");
        verifyNoInteractions(warehouses);
    }

    @Test void partnerPhoneCannotBecomeWarehouseIdentity() {
        MktPromoter p = new MktPromoter(); p.setPhone("13800138000");
        when(promoters.selectOne(any())).thenReturn(p);
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c) -> new WxSessionClient.Session("openid", "session"),
                (key, encrypted, iv) -> "13800138000", contacts, phoneBindingGuard);
        service.setMockLogin(true);
        assertThatThrownBy(() -> service.bindPhone("openid", "13800138000"))
                .isInstanceOf(BizException.class).hasMessageContaining("伙伴身份");
        verify(warehouses, never()).update(any(), any());
    }

    @Test void secondWechatResolvesThroughContactTable() {
        // 主联系人 A 已绑,第二个微信 B 通过联系人表登进同一个云仓
        MktWarehouseContact c = new MktWarehouseContact();
        c.setId(5L); c.setWarehouseId(11L); c.setOpenid("mock:wx-B"); c.setStatus(1);
        when(contacts.selectOne(any())).thenReturn(c);
        MktWarehouse w = warehouse(11L, null, "mock:wx-A", "13800138000");
        when(warehouses.selectById(11L)).thenReturn(w);
        when(jwt.issueForIdentity("wh", 11L)).thenReturn("wh-token");
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c2) -> { throw new AssertionError("mock mode must not call WeChat"); },
                (key, encrypted, iv) -> "13800138000", contacts, phoneBindingGuard);
        service.setMockLogin(true);
        WhAuthService.LoginResult r = service.wxLogin("wx-B");
        assertThat(r.registered()).isTrue();
        assertThat(r.warehouseId()).isEqualTo(11L);
    }

    @Test void contactFromAnotherWarehouseCannotLoginIntoRequestedWarehouse() {
        MktWarehouseContact c = new MktWarehouseContact();
        c.setId(5L); c.setWarehouseId(22L); c.setOpenid("mock:wx-B"); c.setStatus(1);
        when(contacts.selectOne(any())).thenReturn(c);
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c2) -> { throw new AssertionError("mock"); },
                (key, encrypted, iv) -> "13800138000", contacts, phoneBindingGuard);
        service.setMockLogin(true);
        // 请求指定 11 号仓,但该 openid 属于 22 号仓 → 拒绝
        WhAuthService.LoginResult r = service.wxLogin(11L, "wx-B");
        assertThat(r.registered()).isFalse();
    }

    @Test void mismatchedAppIdFailsBeforeWechatCall() {
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c) -> { throw new AssertionError("wrong app must not be called"); }, (k, e, i) -> "unused", contacts, phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("warehouse-app"); service.setAppSecret("secret");
        assertThatThrownBy(() -> service.wxLogin(null, "code", "partner-app"))
                .isInstanceOf(BizException.class).hasMessageContaining("AppID");
    }

    @Test void modernPhoneBindingRequiresOriginalTicket() {
        WxSessionClient client = mock(WxSessionClient.class);
        when(client.exchange("app", "secret", "code")).thenReturn(new WxSessionClient.Session("openid", "key"));
        when(client.exchangePhone("app", "secret", "phone-code")).thenReturn("13800138000");
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit, client, (k, e, i) -> "unused", contacts, phoneBindingGuard);
        service.setMockLogin(false); service.setAppId("app"); service.setAppSecret("secret");
        String ticket = service.wxLogin("code").loginTicket();
        assertThatThrownBy(() -> service.bindPhoneAuthorized("forged", ticket, "phone-code", null, null))
                .isInstanceOf(BizException.class);
        MktWarehouse w = warehouse(11L, null, null, "13800138000");
        when(warehouses.selectOne(any())).thenReturn(w);
        assertThat(service.bindPhoneAuthorized("openid", ticket, "phone-code", null, null).registered()).isTrue();
        verify(phoneBindingGuard).assertCanBind("wh", 11L);
        verify(contacts).insert(argThat((MktWarehouseContact c) -> "openid".equals(c.getOpenid()) && "13800138000".equals(c.getPhone())));
        assertThatThrownBy(() -> service.bindPhoneAuthorized("openid", ticket, "phone-code", null, null))
                .isInstanceOf(BizException.class);
    }


    @Test void exitedWarehouseCannotWechatLoginOrBindPhone() {
        MktWarehouse w = warehouse(11L, null, "mock:wx-11", "13800138000");
        w.setJoinStatus(7);
        when(warehouses.selectOne(any())).thenReturn(w);
        WhAuthService service = new WhAuthService(jwt, warehouses, promoters, audit,
                (a, s, c) -> { throw new AssertionError("mock"); }, (k, e, i) -> "unused", contacts, phoneBindingGuard);
        service.setMockLogin(true);
        assertThatThrownBy(() -> service.wxLogin("wx-11")).isInstanceOf(BizException.class).hasMessageContaining("退出");
        assertThatThrownBy(() -> service.bindPhone("mock:wx-11", "13800138000")).isInstanceOf(BizException.class).hasMessageContaining("退出");
        verifyNoInteractions(jwt);
        verify(warehouses, never()).update(any(), any());
        verify(contacts, never()).insert(any(MktWarehouseContact.class));
    }

    private static MktWarehouse warehouse(Long id, String openid, String contactOpenid, String phone) {
        MktWarehouse w = new MktWarehouse();
        w.setId(id); w.setCode("WH-" + id); w.setName("仓" + id); w.setContactOpenid(contactOpenid);
        w.setPhone(phone); w.setJoinStatus(1); w.setErpStatus(0); return w;
    }
}
