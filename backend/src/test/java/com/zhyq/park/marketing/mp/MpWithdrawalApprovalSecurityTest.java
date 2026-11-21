package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.workflow.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MpWithdrawalApprovalSecurityTest {
    MktWithdrawalMapper withdrawals=mock(MktWithdrawalMapper.class);
    MktWithdrawalService service=mock(MktWithdrawalService.class);
    BizSettings settings=mock(BizSettings.class);
    MktWithdrawalApprovalIdentity identity=new MktWithdrawalApprovalIdentity(settings,mock(SysUserMapper.class),mock(MktPromoterMapper.class),mock(JdbcTemplate.class),mock(MktAuditService.class));
    MktWithdrawalViewService views=new MktWithdrawalViewService(mock(MktPromoterMapper.class),mock(MktPromoterAccountMapper.class),withdrawals,service,
            mock(WorkflowService.class),mock(WorkflowAccessService.class),identity,settings,mock(JdbcTemplate.class));
    MpWithdrawalApprovalController controller=new MpWithdrawalApprovalController(identity,views,withdrawals,service);
    @BeforeEach void setup() {
        when(settings.getString("marketing","withdraw_approver_promoter_id","")).thenReturn("16");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:15",null,List.of(new SimpleGrantedAuthority("ROLE_MP"))));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void normalPartnerCannotListInspectOrApproveOthersWithdrawals(){
        assertThatThrownBy(()->controller.approvals(1,20,null)).isInstanceOf(BizException.class).hasMessageContaining("不是指定提现审批人");
        assertThatThrownBy(()->controller.approval(100L)).isInstanceOf(BizException.class).hasMessageContaining("不是指定提现审批人");
        assertThatThrownBy(()->controller.audit(100L,new MpWithdrawalApprovalController.AuditRequest(true,"同意"))).isInstanceOf(BizException.class);
        verifyNoInteractions(withdrawals,service);
    }
    @Test void applicantCannotReadAnotherPartnersWithdrawal(){
        MktWithdrawal row=new MktWithdrawal();row.setId(100L);row.setPromoterId(99L);when(withdrawals.selectById(100L)).thenReturn(row);
        assertThatThrownBy(()->controller.own(100L)).isInstanceOf(BizException.class).hasMessageContaining("只能查看自己的提现记录");
    }
    @Test void approvalOpinionCannotBeBypassedByCallingApiDirectly(){
        assertThatThrownBy(()->controller.audit(100L,new MpWithdrawalApprovalController.AuditRequest(true," "))).hasMessageContaining("填写审批意见");
        assertThatThrownBy(()->controller.audit(100L,new MpWithdrawalApprovalController.AuditRequest(true,"a".repeat(501)))).hasMessageContaining("最多 500 字");
        verifyNoInteractions(service);
    }
}
