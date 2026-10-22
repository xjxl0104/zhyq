package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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
            MktPromoter exact = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getName, user.getNickname())
                    .eq(MktPromoter::getStatus, 1)
                    .orderByDesc(MktPromoter::getId).last("limit 1"));
            if (exact != null) return exact;
            // 后台昵称常带角色备注，如“丁超(超级管理员)”：去掉末尾括号备注后再按姓名匹配，且必须唯一。
            String plain = withoutRemark(user.getNickname());
            if (StringUtils.hasText(plain) && !plain.equals(user.getNickname())) {
                List<MktPromoter> same = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                        .eq(MktPromoter::getName, plain).eq(MktPromoter::getStatus, 1).last("limit 2"));
                if (same.size() == 1) return same.get(0);
            }
        }
        return null;
    }

    /** 去掉末尾的（…）或(…)备注。 */
    static String withoutRemark(String name) {
        return name == null ? null : name.trim().replaceAll("\\s*[（(][^）)]*[）)]\\s*$", "").trim();
    }
}
