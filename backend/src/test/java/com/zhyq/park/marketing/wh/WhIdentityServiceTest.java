package com.zhyq.park.marketing.wh;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.mp.WxPhoneBindingGuard;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhIdentityServiceTest {
    MktWarehouseContactMapper contacts=mock(MktWarehouseContactMapper.class);
    MktWarehouseMapper warehouses=mock(MktWarehouseMapper.class);
    MktWarehouseOnboardingService onboarding=mock(MktWarehouseOnboardingService.class);
    JwtService jwt=mock(JwtService.class);
    WxPhoneBindingGuard binding=mock(WxPhoneBindingGuard.class);
    MktAuditService audit=mock(MktAuditService.class);
    WhPhoneClaimService phoneClaims=mock(WhPhoneClaimService.class);
    WhIdentityService service=new WhIdentityService(contacts,warehouses,onboarding,jwt,binding,audit,phoneClaims);
    MktWarehouse warehouse;
    MktWarehouseContact member;
    @BeforeAll static void metadata(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,MktWarehouse.class);TableInfoHelper.initTableInfo(a,MktWarehouseContact.class);}
    @BeforeEach void setup(){
        warehouse=new MktWarehouse();warehouse.setId(11L);warehouse.setPhone("13800138000");warehouse.setContact("负责人");warehouse.setJoinStatus(5);
        member=new MktWarehouseContact();member.setId(5L);member.setWarehouseId(11L);member.setRole("member");member.setStatus(1);member.setName("张三");member.setPhone("13900139000");
        when(warehouses.selectById(11L)).thenReturn(warehouse);when(warehouses.selectForUpdate(11L)).thenReturn(warehouse);
        when(contacts.selectById(5L)).thenReturn(member);
        when(contacts.update(isNull(),any())).thenReturn(1);when(jwt.issueForIdentity("wh_contact",5L)).thenReturn("contact-token");
    }
    @Test void samePhoneCanRetainIndependentPartnerAndWarehouseIdentity(){
        when(contacts.selectList(any())).thenReturn(List.of(member));
        var result=service.quickLoginIdentity("wx-member","13900139000","张三");
        assertThat(result).containsEntry("token","contact-token").containsEntry("contactId",5L).containsEntry("warehouseRole","member");
        verify(jwt).issueForIdentity("wh_contact",5L);verify(jwt,never()).issueForIdentity(eq("wh"),anyLong());verifyNoInteractions(onboarding);
        assertThat(((MktWarehouse)result.get("warehouse")).getPhone()).isNull();
    }
    @Test void registeredMemberCannotBePromotedByNameOrOwnerPhone(){
        member.setOpenid("wx");when(contacts.selectOne(any())).thenReturn(member);
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13800138000","负责人")).isInstanceOf(BizException.class).hasMessageContaining("手机号");verifyNoInteractions(jwt,onboarding);
    }
    @Test void memberLoginRequiresProvisionedPhoneAndNeverCreatesOwner() {
        assertThatThrownBy(() -> service.memberLogin("wx", "13900139000", "张三")).hasMessageContaining("尚未登记");
        verifyNoInteractions(onboarding, jwt);
        when(contacts.selectList(any())).thenReturn(List.of(member));
        assertThat(service.memberLogin("wx", "13900139000", "张三")).containsEntry("warehouseRole", "member");
    }

    @Test void twoBindingsRequirePhoneToSelectRole() {
        when(contacts.selectCount(any())).thenReturn(2L);
        assertThat(service.findByOpenid("wx", null)).isNull();
        verify(contacts, never()).selectOne(any());
        verifyNoInteractions(jwt, onboarding);
    }

    @Test void existingBindingCannotBeClaimedByAnotherWechat(){
        member.setOpenid("original");when(contacts.selectList(any())).thenReturn(List.of(member));
        assertThatThrownBy(()->service.quickLoginIdentity("other","13900139000","张三")).isInstanceOf(BizException.class).hasMessageContaining("其他微信");verifyNoInteractions(jwt);
    }
    @Test void disabledMemberCannotFallThroughToRegistrationOrOwner(){
        member.setStatus(0);when(contacts.selectList(any())).thenReturn(List.of(member));
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000","张三")).hasMessageContaining("停用");verifyNoInteractions(onboarding,jwt);
    }
    @Test void disabledOpenidCannotFallBackToLegacyOwner(){
        member.setStatus(0);when(contacts.selectOne(any())).thenReturn(member);
        assertThatThrownBy(()->service.findByOpenid("wx",null)).hasMessageContaining("停用");verify(warehouses,never()).selectOne(any());
    }
    @Test void duplicatePhoneAcrossContactsOrWarehousesFailsClosed(){
        when(contacts.selectList(any())).thenReturn(List.of(member,member));
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000","张三")).hasMessageContaining("多个云仓账号");
        when(contacts.selectList(any())).thenReturn(List.of());when(warehouses.selectList(any())).thenReturn(List.of(warehouse,warehouse));
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13800138000","负责人")).hasMessageContaining("多个云仓");verifyNoInteractions(jwt,onboarding);
    }
    @Test void requestedWarehouseIdIsOnlyHintNeverAuthority(){
        member.setOpenid("wx");when(contacts.selectOne(any())).thenReturn(member);
        assertThat(service.findByOpenid("wx",99L)).isNull();assertThat(service.findByOpenid("wx",11L).contact().getId()).isEqualTo(5L);
    }
    @Test void missingNameOrVerifiedPhoneCannotCreateWarehouse(){
        assertThatThrownBy(()->service.quickLoginIdentity("wx",null,"张三")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000"," ")).isInstanceOf(BizException.class);
        verifyNoInteractions(onboarding,jwt);verify(phoneClaims).lock("13900139000");
    }
    @Test void oldClientMayReuseCompleteNameOnlyAfterVerifiedPhoneAndBindingChecks(){
        member.setOpenid("wx-member");when(contacts.selectList(any())).thenReturn(List.of(member));
        assertThat(service.quickLoginIdentity("wx-member","13900139000",null)).containsEntry("token","contact-token");
        assertThat(member.getName()).isEqualTo("张三");
        member.setRole("owner");member.setPhone(warehouse.getPhone());warehouse.setContactOpenid("wx-member");
        assertThat(service.quickLoginIdentity("wx-member",warehouse.getPhone(),null)).containsEntry("warehouseRole","owner");
    }
    @Test void oldClientCannotFillIncompleteOrPlaceholderNameOrSwitchWechat(){
        when(contacts.selectList(any())).thenReturn(List.of(member));
        member.setName(null);
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000",null)).hasMessageContaining("姓名");
        member.setName("园区伙伴");
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000",null)).hasMessageContaining("姓名");
        member.setName("张三");member.setOpenid("original");
        assertThatThrownBy(()->service.quickLoginIdentity("other","13900139000",null)).hasMessageContaining("其他微信");
        verifyNoInteractions(jwt,onboarding);
    }
    @Test void ownerComesFromOriginalWarehouseRecord(){
        when(warehouses.selectList(any())).thenReturn(List.of(warehouse));
        when(contacts.insert(any(MktWarehouseContact.class))).thenAnswer(i->{((MktWarehouseContact)i.getArgument(0)).setId(9L);return 1;});
        when(jwt.issueForIdentity("wh_contact",9L)).thenReturn("owner-token");
        var result=service.quickLoginIdentity("wx-owner","13800138000","负责人");
        assertThat(result).containsEntry("warehouseRole","owner");verify(binding).assertCanBind("wh",11L);
        verify(contacts).insert(argThat((MktWarehouseContact c)->"owner".equals(c.getRole())&&c.getWarehouseId()==11L));
    }
    @Test void exitedWarehouseCannotLoginEvenWithValidPhone(){
        warehouse.setJoinStatus(7);when(contacts.selectList(any())).thenReturn(List.of(member));
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000","张三")).hasMessageContaining("退出");verifyNoInteractions(jwt);
    }
    @Test void administrativeOwnerPhoneChangeSyncsBothRecordsAndRevokesOldWechat(){
        member.setRole("owner");member.setPhone("13800138000");member.setOpenid("old-wechat");warehouse.setContactOpenid("old-wechat");
        when(contacts.selectOne(any())).thenReturn(member);when(warehouses.update(isNull(),any())).thenReturn(1);
        service.maintainOwner(11L,"李四","13700137000",true);
        verify(phoneClaims).reserve("13700137000",11L,5L);
        verify(contacts).update(isNull(),argThat(q->{var w=(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)q;return w.getSqlSet().contains("auth_revision=auth_revision+1")&&w.getSqlSet().contains("openid")&&w.getParamNameValuePairs().containsValue("13700137000");}));
        verify(warehouses).update(isNull(),argThat(q->((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)q).getSqlSet().contains("contact_openid")));
    }
    @Test void ownerSelfServiceCannotTransferPhone(){assertThatThrownBy(()->service.maintainOwner(11L,"负责人","13700137000",false)).isInstanceOf(BizException.class);verify(contacts,never()).update(any(),any());}
    @Test void onboardingNameUpdateSynchronizesOwnerWithoutChangingBinding(){
        member.setRole("owner");member.setPhone("13800138000");when(contacts.selectOne(any())).thenReturn(member);when(warehouses.update(isNull(),any())).thenReturn(1);
        service.maintainOwner(11L,"更新姓名","13800138000",false);
        verify(contacts).update(isNull(),argThat(q->{var w=(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)q;return w.getParamNameValuePairs().containsValue("更新姓名")&&!w.getSqlSet().contains("openid");}));
    }
    @Test void concurrentPhoneChangeCannotIssueNewIdentityFromOldPhoneSnapshot(){
        when(contacts.selectList(any())).thenReturn(List.of(member));
        var changed=new MktWarehouseContact();changed.setId(5L);changed.setWarehouseId(11L);changed.setStatus(1);changed.setRole("member");changed.setPhone("13700137000");
        when(contacts.selectById(5L)).thenReturn(changed);
        assertThatThrownBy(()->service.quickLoginIdentity("old-wechat","13900139000","张三")).isInstanceOf(BizException.class);verifyNoInteractions(jwt);
    }
    @Test void concurrentDisableCannotBeUndoneByLoginNameUpdate(){
        when(contacts.selectList(any())).thenReturn(List.of(member));
        var changed=new MktWarehouseContact();changed.setId(5L);changed.setWarehouseId(11L);changed.setStatus(0);changed.setRole("member");changed.setPhone("13900139000");
        when(contacts.selectById(5L)).thenReturn(changed);
        assertThatThrownBy(()->service.quickLoginIdentity("wx","13900139000","张三")).hasMessageContaining("停用");verify(contacts,never()).update(any(),any());verifyNoInteractions(jwt);
    }
    @Test void concurrentOwnerPhoneChangeCannotBindOldPhoneAfterLegacyWarehouseLookup(){
        when(warehouses.selectList(any())).thenReturn(List.of(warehouse));
        var changed=new MktWarehouse();changed.setId(11L);changed.setJoinStatus(5);changed.setPhone("13700137000");
        when(warehouses.selectForUpdate(11L)).thenReturn(changed);
        assertThatThrownBy(()->service.quickLoginIdentity("wx-old","13800138000","负责人")).isInstanceOf(BizException.class);
        verify(contacts,never()).insert(any(MktWarehouseContact.class));verifyNoInteractions(jwt,binding);
    }
    @Test void establishedLegacyOwnerWithMissingPhoneCompletesOriginalWarehouseUsingVerifiedPhone(){
        member.setRole("owner");member.setPhone(null);member.setOpenid("original");warehouse.setPhone(null);warehouse.setContactOpenid("original");
        when(contacts.selectOne(any())).thenReturn(member);when(warehouses.update(isNull(),any())).thenReturn(1);
        var result=service.quickLoginIdentity("original","13900139000","张三");
        assertThat(result).containsEntry("warehouseId",11L).containsEntry("warehouseRole","owner");
        verify(phoneClaims).reserve("13900139000",11L,5L);verifyNoInteractions(onboarding);verify(contacts,never()).insert(any(MktWarehouseContact.class));
        assertThat(member.getPhone()).isEqualTo("13900139000");
    }
    @Test void incompleteLegacyOwnerCannotOverwriteCanonicalPhoneOrAdoptDifferentOpenid(){
        member.setRole("owner");member.setPhone(null);member.setOpenid("original");warehouse.setContactOpenid("original");when(contacts.selectOne(any())).thenReturn(member);
        assertThatThrownBy(()->service.quickLoginIdentity("original","13900139000","张三")).isInstanceOf(BizException.class);
        warehouse.setPhone(null);warehouse.setContactOpenid("different");
        assertThatThrownBy(()->service.quickLoginIdentity("original","13900139000","张三")).isInstanceOf(BizException.class);
        verify(contacts,never()).update(any(),any());verifyNoInteractions(onboarding,jwt);
    }
    @Test void incompleteLegacyOwnerStillCannotTakeAnotherWarehouseStaffPhone(){
        member.setRole("owner");member.setPhone(null);member.setOpenid("original");warehouse.setPhone(null);warehouse.setContactOpenid("original");when(contacts.selectOne(any())).thenReturn(member);
        doThrow(new BizException(409,"已关联云仓账号")).when(phoneClaims).reserve("13900139000",11L,5L);
        assertThatThrownBy(()->service.quickLoginIdentity("original","13900139000","张三")).hasMessageContaining("已关联");verify(contacts,never()).update(any(),any());
    }
    @Test void legacyWxLoginRequestsVerifiedProfileCompletionInsteadOfIssuingIncompleteIdentity(){
        member.setRole("owner");member.setPhone(null);member.setOpenid("original");when(contacts.selectOne(any())).thenReturn(member);
        assertThat(service.findByOpenid("original",null)).isNull();verifyNoInteractions(jwt);
    }
    @Test void oldPasswordCannotIssueOwnerTokenAfterAdministrativePhoneChange(){
        member.setRole("owner");member.setPhone(warehouse.getPhone());member.setPasswordResetRequired(true);when(contacts.selectOne(any())).thenReturn(member);
        assertThatThrownBy(()->service.issueOwnerToken(11L)).hasMessageContaining("重新设置密码");verifyNoInteractions(jwt);
    }
    @Test void resettingChangedPhonePasswordRequiresCurrentOwnerAndNewWechatBinding(){
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("wh:11:contact:5",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WH_OWNER"))));
        try {
            member.setRole("owner");member.setPhone(warehouse.getPhone());member.setPasswordResetRequired(true);
            assertThatThrownBy(()->service.beginOwnerPasswordSetup(11L)).hasMessageContaining("微信授权");
            member.setOpenid("new-verified-wechat");warehouse.setContactOpenid("new-verified-wechat");assertThat(service.beginOwnerPasswordSetup(11L)).isTrue();
            service.finishOwnerPasswordSetup(11L);
            verify(contacts).update(isNull(),argThat(q->((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)q).getSqlSet().contains("auth_revision=auth_revision+1")));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
