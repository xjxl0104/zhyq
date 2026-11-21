package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** 移动端审批仅接受运维显式配置的两端身份绑定，不通过姓名、手机号或 P4 等级推断权限。 */
@Service
@RequiredArgsConstructor
public class MktWithdrawalApprovalIdentity {
    private final BizSettings settings;
    private final SysUserMapper users;
    private final MktPromoterMapper promoters;
    private final JdbcTemplate jdbc;
    private final MktAuditService audit;

    public Map<String,Object> configuration() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("username", settings.getString("marketing", "withdraw_approver_username", ""));
        result.put("promoterId", settings.getString("marketing", "withdraw_approver_promoter_id", ""));
        var user = configuredUser(); result.put("approverName",user == null ? null : user.getNickname());
        return result;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void configure(String username, Long promoterId) {
        if (username == null || username.isBlank() || promoterId == null || promoterId <= 0) throw new BizException("请明确选择审批员工账号及其伙伴账号");
        var user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,username.trim()).eq(SysUser::getStatus,1));
        var promoter = promoters.selectById(promoterId);
        if (user == null || promoter == null || !Integer.valueOf(1).equals(promoter.getStatus())) throw new BizException("审批员工和伙伴账号均须存在且正常启用");
        var nodes = jdbc.queryForList("SELECT approver_type,approver_value FROM wf_node WHERE deleted=0 AND definition_id="
                + "(SELECT MAX(id) FROM wf_definition WHERE deleted=0 AND status=1 AND biz_type='withdrawal')");
        if (nodes.isEmpty() || nodes.stream().anyMatch(n -> !"user".equals(n.get("approver_type")) || !user.getUsername().equals(n.get("approver_value"))))
            throw new BizException("请先在审批流程中将提现审批节点指定给该员工账号，再保存移动端绑定");
        save("withdraw_approver_username",user.getUsername());
        save("withdraw_approver_promoter_id",promoterId.toString());
    }

    private void save(String key,String value) {
        var rows = jdbc.queryForList("SELECT svalue FROM biz_setting WHERE module='marketing' AND skey=? AND deleted=0 FOR UPDATE",key);
        String before = rows.isEmpty() ? null : String.valueOf(rows.get(0).get("svalue"));
        if (rows.isEmpty()) jdbc.update("INSERT INTO biz_setting(module,skey,svalue,remark,create_by,create_time,update_by,update_time) VALUES('marketing',?,?,?, ?,NOW(),?,NOW())",
                key,value,"提现审批专用身份绑定，仅超级管理员可通过审批身份配置接口修改",MktAuditService.currentOperator(),MktAuditService.currentOperator());
        else jdbc.update("UPDATE biz_setting SET svalue=?,update_by=?,update_time=NOW() WHERE module='marketing' AND skey=? AND deleted=0",value,MktAuditService.currentOperator(),key);
        audit.log("withdrawal.approver-binding","setting",null,key,before,value);
    }

    public SysUser configuredUser() {
        String username = settings.getString("marketing", "withdraw_approver_username", "");
        if (username.isBlank()) return null;
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1).last("limit 1"));
        return user;
    }

    public SysUser forPromoter(Long promoterId) {
        String configured = settings.getString("marketing", "withdraw_approver_promoter_id", "");
        if (promoterId == null || !promoterId.toString().equals(configured)) return null;
        var promoter = promoters.selectById(promoterId);
        if (promoter == null || !Integer.valueOf(1).equals(promoter.getStatus())) return null;
        return configuredUser();
    }

    public boolean isConfiguredConsoleApprover() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
                .anyMatch(a -> List.of("ROLE_MP", "ROLE_WH").contains(a.getAuthority()))) return false;
        SysUser user = configuredUser();
        return user != null && user.getUsername().equals(auth.getName());
    }

    /** 仅在这一同步服务调用内使用绑定的员工身份；不签发员工 token，不授予角色或通用后台权限。 */
    public <T> T asApprover(Long promoterId, Supplier<T> action) {
        SysUser user = forPromoter(promoterId);
        if (user == null) throw new BizException(403, "当前账号不是指定提现审批人");
        var previous = SecurityContextHolder.getContext();
        var restricted = SecurityContextHolder.createEmptyContext();
        restricted.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        SecurityContextHolder.setContext(restricted);
        try { return action.get(); }
        finally { SecurityContextHolder.setContext(previous); }
    }
}
