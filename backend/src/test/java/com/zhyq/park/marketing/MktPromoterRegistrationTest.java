package com.zhyq.park.marketing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktPromoterRegistrationTest {
    @Mock MktPromoterMapper promoters;
    @Mock SysUserMapper users;
    @Mock MktAuditService audit;
    @Mock JdbcTemplate jdbc;
    MktPromoterService service;

    @BeforeEach void setup() {
        service = new MktPromoterService(promoters, users, audit, jdbc);
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "registration-test");
        TableInfoHelper.initTableInfo(assistant, MktPromoter.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test void selfRegistrationCannotBypassInviteRequirement() {
        for (String invite : new String[] { null, "", " ", "ab", "ABCDEFGHI", "ABC0123" }) {
            assertThatThrownBy(() -> service.register(profile(), invite, "mp"))
                    .isInstanceOf(BizException.class).hasMessageContaining("邀请码");
        }
        verifyNoInteractions(promoters, users, audit, jdbc);
    }

    @Test void validInviteBindsParentAndNewUserCannotChooseP4() {
        MktPromoter parent = profile(); parent.setId(8L); parent.setStatus(1); parent.setPath("/2/8/");
        when(promoters.selectOne(any())).thenAnswer(call -> {
            LambdaQueryWrapper<MktPromoter> query = call.getArgument(0);
            query.getSqlSegment();
            assertThat(query.getParamNameValuePairs().values()).contains("ABCD2345");
            return parent;
        });
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(call -> {
            ((MktPromoter) call.getArgument(0)).setId(9L); return 1;
        });
        MktPromoter p = profile(); p.setPositionCode("P4");
        MktPromoter saved = service.register(p, " abcd2345 ", "mp");
        assertThat(saved.getParentId()).isEqualTo(8L);
        assertThat(saved.getPath()).isEqualTo("/2/8/9/");
        assertThat(saved.getPositionCode()).isEqualTo("P1");
        assertThat(saved.getInviteDeadline()).isNull();
        assertThat(saved.getBindTime()).isNotNull();
    }

    @Test void missingInviterDoesNotCreateAccount() {
        assertThatThrownBy(() -> service.register(profile(), "ABCD2345", "mp"))
                .isInstanceOf(BizException.class).hasMessageContaining("邀请码不存在");
        verify(promoters, never()).insert(any(MktPromoter.class));
    }

    @Test void operatorCanStillCreateRootP4WithoutInviter() {
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(call -> {
            ((MktPromoter) call.getArgument(0)).setId(9L); return 1;
        });
        MktPromoter root = profile(); root.setPositionCode("P4");
        MktPromoter saved = service.register(root, null, "manual");
        assertThat(saved.getPositionCode()).isEqualTo("P4");
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getPath()).isEqualTo("/9/");
    }

    private MktPromoter actor(String position) {
        MktPromoter a = new MktPromoter(); a.setId(5L); a.setName("丁超"); a.setStatus(1); a.setPositionCode(position);
        a.setInviteCode("ABCD2345"); a.setPath("/5/"); return a;
    }

    @Test void loginMemberNeedsPhoneAndHangsUnderActorOneLevelLower() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P4"));
        when(promoters.selectOne(any())).thenReturn(actor("P4"));
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(call -> {
            ((MktPromoter) call.getArgument(0)).setId(20L); return 1;
        });
        MktPromoter saved = service.addTeamMember(5L, " 张三 ", "13900139000");
        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getPhone()).isEqualTo("13900139000");
        assertThat(saved.getParentId()).isEqualTo(5L);
        assertThat(saved.getPositionCode()).isEqualTo("P3");
        assertThat(saved.getPath()).isEqualTo("/5/20/");
        assertThat(saved.getSource()).isEqualTo("team");
    }

    @Test void nonLoginMemberGetsPlaceholderPhoneThatCannotLogIn() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P3"));
        when(promoters.selectOne(any())).thenReturn(actor("P3"));
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(call -> {
            ((MktPromoter) call.getArgument(0)).setId(21L); return 1;
        });
        MktPromoter saved = service.addTeamMember(5L, "李四", "");
        assertThat(saved.getPhone()).startsWith("p:");
        assertThat(saved.getPositionCode()).isEqualTo("P2");
        assertThat(saved.getOpenid()).isNull();
    }

    @Test void invalidPhoneDuplicatePhoneAndBlankNameAreRejected() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P4"));
        assertThatThrownBy(() -> service.addTeamMember(5L, "王五", "123")).isInstanceOf(BizException.class).hasMessageContaining("11 位手机号");
        assertThatThrownBy(() -> service.addTeamMember(5L, "  ", "13900139000")).isInstanceOf(BizException.class).hasMessageContaining("姓名");
        when(promoters.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.addTeamMember(5L, "王五", "13900139000")).isInstanceOf(BizException.class).hasMessageContaining("已是伙伴");
        verify(promoters, never()).insert(any(MktPromoter.class));
    }

    @Test void onlyP4CanChooseThePositionOfANewMember() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P4"));
        when(promoters.selectOne(any())).thenReturn(actor("P4"));
        when(promoters.insert(any(MktPromoter.class))).thenAnswer(call -> {
            ((MktPromoter) call.getArgument(0)).setId(22L); return 1;
        });
        assertThat(service.addTeamMember(5L, "甲", null, "p1").getPositionCode()).isEqualTo("P1");
        assertThatThrownBy(() -> service.addTeamMember(5L, "乙", null, "P4")).isInstanceOf(BizException.class).hasMessageContaining("P1、P2 或 P3");
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P3"));
        when(promoters.selectOne(any())).thenReturn(actor("P3"));
        assertThat(service.addTeamMember(5L, "丙", null, "P2").getPositionCode()).isEqualTo("P2");
        assertThatThrownBy(() -> service.addTeamMember(5L, "丁", null, "P1")).isInstanceOf(BizException.class).hasMessageContaining("只有 P4");
    }

    private MktPromoter member(Long id, Long parentId, String phone, String path) {
        MktPromoter m = new MktPromoter(); m.setId(id); m.setParentId(parentId); m.setPhone(phone); m.setPath(path); m.setStatus(1); return m;
    }

    @Test void parentCanAddPhoneLaterButOnlyForMembersWithoutRealPhone() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P3"));
        when(promoters.selectForUpdate(30L)).thenReturn(member(30L, 5L, "p:abc", "/5/30/"));
        MktPromoter saved = service.setMemberPhone(5L, 30L, "13700137000");
        assertThat(saved.getPhone()).isEqualTo("13700137000");
        verify(promoters).update(isNull(), any());

        when(promoters.selectForUpdate(31L)).thenReturn(member(31L, 5L, "13600136000", "/5/31/"));
        assertThatThrownBy(() -> service.setMemberPhone(5L, 31L, "13700137001")).isInstanceOf(BizException.class).hasMessageContaining("已经可以登录");
    }

    @Test void phoneCannotBeAddedByOutsidersOrWithBadOrDuplicateNumber() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P3"));
        when(promoters.selectForUpdate(32L)).thenReturn(member(32L, 9L, "p:abc", "/9/32/"));
        assertThatThrownBy(() -> service.setMemberPhone(5L, 32L, "13700137000")).isInstanceOf(BizException.class).hasMessageContaining("自己团队");
        when(promoters.selectForUpdate(33L)).thenReturn(member(33L, 5L, "p:abc", "/5/33/"));
        assertThatThrownBy(() -> service.setMemberPhone(5L, 33L, "123")).isInstanceOf(BizException.class).hasMessageContaining("11 位手机号");
        when(promoters.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.setMemberPhone(5L, 33L, "13700137000")).isInstanceOf(BizException.class).hasMessageContaining("已是伙伴");
        verify(promoters, never()).update(any(), any());
    }

    @Test void p4CanAddPhoneForAnyoneInTheirTeam() {
        when(promoters.selectForUpdate(5L)).thenReturn(actor("P4"));
        when(promoters.selectForUpdate(34L)).thenReturn(member(34L, 30L, "p:abc", "/5/30/34/"));
        assertThat(service.setMemberPhone(5L, 34L, "13700137002").getPhone()).isEqualTo("13700137002");
    }

    @Test void frozenActorCannotAddMembers() {
        MktPromoter frozen = actor("P4"); frozen.setStatus(2);
        when(promoters.selectForUpdate(5L)).thenReturn(frozen);
        assertThatThrownBy(() -> service.addTeamMember(5L, "赵六", null)).isInstanceOf(BizException.class);
    }

    private MktPromoter profile() {
        MktPromoter p = new MktPromoter(); p.setPhone("13800138000"); p.setName("测试伙伴"); return p;
    }
}
