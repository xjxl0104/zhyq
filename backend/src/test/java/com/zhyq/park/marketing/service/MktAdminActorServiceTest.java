package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 后台账号只按手机号精确关联伙伴，不再按昵称/姓名匹配。 */
class MktAdminActorServiceTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    private final MktAdminActorService service = new MktAdminActorService(users, promoters);

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(SysUser.class, MktPromoter.class)) TableInfoHelper.initTableInfo(assistant, type);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }
    private static SysUser user(String nickname, String phone) {
        var user = new SysUser(); user.setUsername("ding"); user.setNickname(nickname); user.setPhone(phone); return user;
    }

    @Test void 手机号一致的后台账号关联到对应的伙伴() {
        login("ding");
        when(users.selectOne(any())).thenReturn(user("丁超(超级管理员)", "18824642574"));
        var promoter = new MktPromoter(); promoter.setId(4L); promoter.setName("丁超");
        when(promoters.selectOne(any())).thenReturn(promoter);
        assertThat(service.currentPromoter()).isSameAs(promoter);
    }

    @Test void 没有手机号时即使昵称与伙伴同名也不关联() {
        login("ding");
        when(users.selectOne(any())).thenReturn(user("丁超", null));
        assertThat(service.currentPromoter()).isNull();
        verifyNoInteractions(promoters);
    }

    @Test void 手机号格式不对或后台用户不存在或系统操作都不关联() {
        login("ding");
        when(users.selectOne(any())).thenReturn(user("丁超", "123"));
        assertThat(service.currentPromoter()).isNull();
        when(users.selectOne(any())).thenReturn(null);
        assertThat(service.currentPromoter()).isNull();
        SecurityContextHolder.clearContext();   // 没有登录态时操作人是 system
        assertThat(service.currentPromoter()).isNull();
        verifyNoInteractions(promoters);
    }
}
