package com.zhyq.park.marketing.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktAccountAdminServiceTest {
    MktAccountControl control = mock(MktAccountControl.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MktPromoterService promoters = mock(MktPromoterService.class);
    MktWarehouseOnboardingService warehouses = mock(MktWarehouseOnboardingService.class);
    MktPromoterDeletionService deletion = mock(MktPromoterDeletionService.class);
    MktCredentialMapper credentials = mock(MktCredentialMapper.class);
    SysAuditMapper audits = mock(SysAuditMapper.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    MktAccountAdminService service = new MktAccountAdminService(control,jdbc,promoters,warehouses,deletion,credentials,encoder,audits,new ObjectMapper());
    @BeforeEach void setup() {
        when(control.requireSuperAdmin()).thenReturn(2L);
        when(control.state(anyString(),anyLong())).thenReturn(new MktAccountControl.State(false,false,0));
        when(jdbc.queryForList(startsWith("SELECT id,name"),any(Object[].class)))
                .thenReturn(List.of(Map.of("id",7L,"name","测试账号","invite_code","ABCD2345")));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
    }
    @Test void everyOperationDeniesOrdinaryPartnerBeforeReadingOrWritingAccounts() {
        doThrow(new BizException(403,"仅超级管理员")).when(control).requireSuperAdmin();
        assertThatThrownBy(() -> service.page("mp","",1,20)).hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> service.create(null)).hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> service.changeInvite(7L,null)).hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> service.setDisabled("mp",7L,null)).hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> service.delete("wh",7L,"测试")).hasMessageContaining("超级管理员");
        verifyNoInteractions(jdbc,promoters,warehouses,deletion,credentials,audits);
    }
    @Test void adminCannotDeleteOrDisableSelfOrAnotherAdmin() {
        assertThatThrownBy(() -> service.delete("mp",2L,"测试")).hasMessageContaining("超级管理员");
        assertThatThrownBy(() -> service.setDisabled("mp",2L,new MktAccountAdminService.StatusRequest(true,false,"测试"))).hasMessageContaining("超级管理员");
        when(control.state("mp",7L)).thenReturn(new MktAccountControl.State(true,false,1));
        assertThatThrownBy(() -> service.delete("mp",7L,"测试")).hasMessageContaining("超级管理员");
        verifyNoInteractions(jdbc,deletion);
    }
    @Test void inviteEditNormalizesAndAuditsWithoutChangingParent() {
        service.changeInvite(7L,new MktAccountAdminService.InviteRequest(" newc2345 ","ABCD2345","调整邀请码"));
        verify(jdbc).update(startsWith("UPDATE crm_promoter SET invite_code="),eq("NEWC2345"),eq("system"),eq(7L),eq("ABCD2345"));
        ArgumentCaptor<SysAudit> a = ArgumentCaptor.forClass(SysAudit.class); verify(audits).insert(a.capture());
        assertThat(a.getValue().getBeforeJson()).contains("ABCD2345"); assertThat(a.getValue().getAfterJson()).contains("NEWC2345");
    }
    @Test void staleAndDuplicateInviteCodesCannotOverwrite() {
        assertThatThrownBy(() -> service.changeInvite(7L,new MktAccountAdminService.InviteRequest("NEWC2345","OLDX2345","测试"))).hasMessageContaining("已变化");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.changeInvite(7L,new MktAccountAdminService.InviteRequest("NEWC2345","ABCD2345","测试"))).hasMessageContaining("已被使用");
        verifyNoInteractions(audits);
    }
    @Test void statusChangesInvalidatePriorSessionsAndDetectStaleForms() {
        service.setDisabled("wh",7L,new MktAccountAdminService.StatusRequest(true,false,"暂停账号"));
        verify(jdbc).update(contains("revision=revision+1"),eq("wh"),eq(7L),eq(1),eq("system"));
        when(control.state("wh",7L)).thenReturn(new MktAccountControl.State(false,true,1));
        assertThatThrownBy(() -> service.setDisabled("wh",7L,new MktAccountAdminService.StatusRequest(true,false,"过期页面"))).hasMessageContaining("已变化");
    }
    @Test void partnerDeletionReusesExistingBusinessHistoryGuards() {
        service.delete("mp",7L,"误建账号"); verify(deletion).delete(7L,"误建账号");
    }
    @Test void warehouseWithHistoryCannotBeDeleted() {
        when(jdbc.queryForList(contains("FROM crm_referral_order WHERE"),eq(Long.class),any(Object[].class))).thenReturn(List.of(8L));
        assertThatThrownBy(() -> service.delete("wh",7L,"误建账号")).hasMessageContaining("关联业务");
        verify(jdbc,never()).update(anyString(),any(Object[].class)); verifyNoInteractions(audits);
    }
    @Test void emptyWarehouseDeleteRevokesBothPasswordAndWechatWithoutDeletingHistory() {
        service.delete("wh",7L,"误建账号");
        verify(jdbc).update(startsWith("UPDATE crm_marketing_credential"),eq("system"),eq(7L));
        verify(jdbc).update(startsWith("UPDATE crm_warehouse_contact SET openid=NULL"),eq("system"),eq(7L));
        verify(jdbc).update(startsWith("UPDATE crm_warehouse SET deleted=1"),eq("system"),eq(7L));
        verify(jdbc,never()).update(startsWith("DELETE"),any(Object[].class));
    }
    @Test void creationReturnsNoTokenPasswordOrElevatedRole() {
        when(promoters.registerManaged(any(),isNull())).thenAnswer(i -> {
            MktPromoter p = i.getArgument(0); p.setId(7L); p.setInviteCode("ABCD2345"); return p;
        });
        when(credentials.insert(any(MktCredential.class))).thenReturn(1);
        var result = service.create(new MktAccountAdminService.CreateRequest("mp","新伙伴",null," NEWUSER ","secret-value",null,"新增"));
        assertThat(result).containsEntry("username","newuser").doesNotContainKeys("password","token","superAdmin");
        var c = ArgumentCaptor.forClass(MktCredential.class); verify(credentials).insert(c.capture());
        assertThat(c.getValue().getPasswordHash()).startsWith("{bcrypt-sha256}").doesNotContain("secret-value");
        assertThat(c.getValue().getAgreedAt()).isNull();
        var a = ArgumentCaptor.forClass(SysAudit.class); verify(audits).insert(a.capture());
        assertThat(a.getValue().getAfterJson()).doesNotContain("secret-value","password");
    }
    @Test void auditFailureAbortsMutationTransaction() {
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        assertThatThrownBy(() -> service.changeInvite(7L,new MktAccountAdminService.InviteRequest("NEWC2345","ABCD2345","测试"))).hasMessageContaining("审计保存失败");
    }
    @Test void invalidPaginationAndTypesAreRejected() {
        assertThatThrownBy(() -> service.page("admin","",1,20)).hasMessageContaining("类型");
        assertThatThrownBy(() -> service.page("mp","",1,1000)).hasMessageContaining("分页");
        assertThatThrownBy(() -> service.page("mp","a".repeat(101),1,20)).hasMessageContaining("100字");
        verifyNoInteractions(jdbc);
    }
}
