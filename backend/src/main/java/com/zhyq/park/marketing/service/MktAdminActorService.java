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
 * 后台操作员 → 伙伴身份解析:佣金分配等"仅 P4 可设置"的后台能力,
 * 通过 sys_user 的手机号(优先)或昵称匹配 crm_promoter 找到操作员对应的伙伴。
 * 未关联伙伴账号的后台用户拿不到 P4 专属操作权。
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
        if (user == null) return null;
        if (StringUtils.hasText(user.getPhone()) && user.getPhone().matches("^1\\d{10}$")) {
            MktPromoter byPhone = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getPhone, user.getPhone()).orderByDesc(MktPromoter::getId).last("limit 1"));
            if (byPhone != null) return byPhone;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getName, user.getNickname())
                    .eq(MktPromoter::getStatus, 1)
                    .orderByDesc(MktPromoter::getId).last("limit 1"));
        }
        return null;
    }
}
