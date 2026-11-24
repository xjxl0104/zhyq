package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.admin.MktAccountControl;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台操作员 → 伙伴身份解析:佣金分配、调整层级等后台能力,
 * 只通过 sys_user 的手机号精确匹配 crm_promoter 找到操作员对应的伙伴。
 * 后台修改佣金分配进一步限制为超级管理员（丁超、杨洪，以 crm_marketing_account_control.super_admin=1 为准）。
 */
@Service
@RequiredArgsConstructor
public class MktAdminActorService {

    private final SysUserMapper sysUserMapper;
    private final MktPromoterMapper promoterMapper;
    @Autowired(required = false)
    private MktAccountControl accountControl;

    /** 当前后台操作员关联的伙伴;未关联返回 null。 */
    public MktPromoter currentPromoter() {
        String operator = MktAuditService.currentOperator();
        if (!StringUtils.hasText(operator) || "system".equals(operator)) return null;
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, operator).last("limit 1"));
        if (user == null || !StringUtils.hasText(user.getPhone()) || !user.getPhone().matches("^1\\d{10}$")) return null;
        return promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getPhone, user.getPhone()).orderByDesc(MktPromoter::getId).last("limit 1"));
    }

    /** 后台是否属于允许修改佣金分配的超级管理员（丁超、杨洪）。 */
    public boolean isCommissionSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) return false;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name) || name.startsWith("mp:") || name.startsWith("wh:")) return false;
        boolean hasMpOrWh = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MP".equals(a.getAuthority()) || "ROLE_WH".equals(a.getAuthority()));
        if (hasMpOrWh) return false;
        MktPromoter promoter = currentPromoter();
        if (promoter == null || !Integer.valueOf(1).equals(promoter.getStatus())) return false;
        return accountControl != null && accountControl.isSuperAdmin(promoter.getId());
    }
}
