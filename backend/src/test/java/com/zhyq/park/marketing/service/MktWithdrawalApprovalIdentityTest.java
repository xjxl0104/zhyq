package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MktWithdrawalApprovalIdentityTest {
    BizSettings settings=mock(BizSettings.class);
    SysUserMapper users=mock(SysUserMapper.class);
    MktPromoterMapper promoters=mock(MktPromoterMapper.class);
    MktWithdrawalApprovalIdentity service=new MktWithdrawalApprovalIdentity(settings,users,promoters,mock(JdbcTemplate.class),mock(MktAuditService.class));

    @BeforeEach void setup() {
        for (Class<?> type:List.of(SysUser.class,MktPromoter.class)) TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),type);
        when(settings.getString("marketing","withdraw_approver_promoter_id","")).thenReturn("16");
        when(settings.getString("marketing","withdraw_approver_username","")).thenReturn("yangzong");
        MktPromoter p=new MktPromoter();p.setId(16L);p.setStatus(1);when(promoters.selectById(16L)).thenReturn(p);
        SysUser user=new SysUser();user.setId(5L);user.setUsername("yangzong");user.setStatus(1);when(users.selectOne(any())).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:16",null,List.of(new SimpleGrantedAuthority("ROLE_MP"))));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void unrelatedPartnerCannotAcquireApprovalIdentity(){
        assertThat(service.forPromoter(15L)).isNull();
        assertThatThrownBy(()->service.asApprover(15L,()->"not-called")).isInstanceOf(BizException.class);
        verifyNoInteractions(users);
    }
    @Test void missingOrInactiveBindingFailsClosed(){
        when(settings.getString("marketing","withdraw_approver_username","")).thenReturn("");
        assertThat(service.forPromoter(16L)).isNull();
        when(settings.getString("marketing","withdraw_approver_username","")).thenReturn("yangzong");
        when(users.selectOne(any())).thenReturn(null);assertThat(service.forPromoter(16L)).isNull();
        when(promoters.selectById(16L)).thenReturn(null);assertThat(service.forPromoter(16L)).isNull();
    }
    @Test void scopedIdentityHasNoAdminRoleAndIsAlwaysRestored(){
        var original=SecurityContextHolder.getContext();
        assertThat(service.asApprover(16L,()->{
            var auth=SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getName()).isEqualTo("yangzong");assertThat(auth.getAuthorities()).isEmpty();return "ok";
        })).isEqualTo("ok");
        assertThat(SecurityContextHolder.getContext()).isSameAs(original);
        assertThatThrownBy(()->service.asApprover(16L,()->{throw new BizException("failed");})).hasMessageContaining("failed");
        assertThat(SecurityContextHolder.getContext()).isSameAs(original);
        assertThat(service.isConfiguredConsoleApprover()).isFalse();
    }
}
