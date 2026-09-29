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
        for (String invite : new String[] { null, "", " ", "invalid" }) {
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

    private MktPromoter profile() {
        MktPromoter p = new MktPromoter(); p.setPhone("13800138000"); p.setName("测试伙伴"); return p;
    }
}
