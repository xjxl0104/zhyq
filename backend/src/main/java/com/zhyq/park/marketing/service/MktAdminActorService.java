package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台操作员 → 伙伴身份解析:佣金分配、调整层级等"仅 P4 可设置"的后台能力,
 * 只通过 sys_user 的手机号精确匹配 crm_promoter 找到操作员对应的伙伴。
 * 不再按昵称/姓名匹配（昵称可被改成与某位 P4 同名，会冒用其定价权）。
 * 没有手机号或手机号没有对应伙伴档案的后台用户拿不到 P4 专属操作权。
 */
@Service
@RequiredArgsConstructor
public class MktAdminActorService {

    private final SysUserMapper sysUserMapper;
    private final MktPromoterMapper promoterMapper;

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
}
