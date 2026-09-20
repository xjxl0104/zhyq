package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 伙伴档案(PARK-MKT-001 §2.1):注册/录入、绑上级、改上级(整棵子树 path 一起改)、冻结/解冻/审核。
 * 邀请码 8 位去掉 0/O/1/I;上级终身绑定,只有运营可改并写审计;不能是自己、不能成环。
 */
@Service
@RequiredArgsConstructor
public class MktPromoterService {

    public static final int ST_NORMAL = 1;
    public static final int ST_FROZEN = 2;
    public static final int ST_PENDING = 3;
    public static final int ST_EXITED = 4;

    private static final String INVITE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int INVITE_LEN = 8;
    private static final SecureRandom RND = new SecureRandom();
    private static final String BIZ_TYPE = "promoter";

    private final MktPromoterMapper promoterMapper;
    private final SysUserMapper sysUserMapper;
    private final MktAuditService auditService;
    private final JdbcTemplate jdbc;

    /** 后台手工录入(阶段 A)或小程序注册(阶段 B)共用:校验手机号唯一、找上级、生成邀请码、算 path。 */
    @Transactional
    public MktPromoter register(MktPromoter p, String parentInviteCode, String source) {
        if (!StringUtils.hasText(p.getPhone()) || !p.getPhone().matches("^1\\d{10}$")) {
            throw new BizException("手机号格式不正确");
        }
        if (promoterMapper.selectCount(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, p.getPhone())) > 0) {
            throw new BizException("该手机号已注册为伙伴");
        }
        MktPromoter parent = null;
        if (StringUtils.hasText(parentInviteCode)) {
            parent = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getInviteCode, parentInviteCode.trim().toUpperCase()).last("limit 1"));
            if (parent == null) {
                throw new BizException("邀请码不存在: " + parentInviteCode);
            }
            if (parent.getStatus() == null || parent.getStatus() == ST_EXITED) {
                throw new BizException("上级已退出,不能绑定");
            }
        }
        p.setParentId(parent == null ? null : parent.getId());
        p.setBindTime(parent == null ? null : LocalDateTime.now());
        if (!StringUtils.hasText(p.getPositionCode())) {
            p.setPositionCode("P1");
        }
        p.setPositionSince(LocalDateTime.now());
        p.setStatus(p.getStatus() == null ? ST_NORMAL : p.getStatus());
        p.setIsInternal(isInternal(p.getPhone()) ? 1 : 0);
        p.setSource(source);
        p.setPath("/");
        for (int attempt = 0; attempt < 5; attempt++) {
            p.setInviteCode(randomInvite());
            try {
                promoterMapper.insert(p);
                break;
            } catch (DuplicateKeyException dup) {
                if (attempt == 4) throw new BizException("生成邀请码失败,请重试");
            }
        }
        String path = (parent == null ? "/" : parent.getPath()) + p.getId() + "/";
        promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, p.getId()).set(MktPromoter::getPath, path));
        p.setPath(path);
        auditService.log("promoter.register", BIZ_TYPE, p.getId(), source);
        return p;
    }

    /** 改上级:不能是自己、新上级不能在自己子树里(成环);整棵子树 path 前缀替换,一个事务。 */
    @Transactional
    public void changeParent(Long id, Long newParentId, String reason) {
        requireReason(reason);
        MktPromoter me = require(id);
        if (newParentId != null && newParentId.equals(id)) {
            throw new BizException("上级不能是自己");
        }
        String oldPrefix = me.getPath();
        String newParentPath = "/";
        if (newParentId != null) {
            MktPromoter np = require(newParentId);
            if (np.getPath().startsWith(oldPrefix)) {
                throw new BizException("新上级在当前伙伴的团队里,会成环");
            }
            if (np.getStatus() == null || np.getStatus() == ST_EXITED) {
                throw new BizException("新上级已退出");
            }
            newParentPath = np.getPath();
        }
        String newPrefix = newParentPath + id + "/";
        int updated = promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, id)
                .eq(MktPromoter::getPath, oldPrefix)
                .set(MktPromoter::getParentId, newParentId)
                .set(MktPromoter::getBindTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("伙伴 " + id + " 已被修改,请刷新");
        }
        // 自己 + 整棵子树:path 的 oldPrefix 段换成 newPrefix
        jdbc.update("UPDATE crm_promoter SET path = CONCAT(?, SUBSTRING(path, ?)) WHERE path LIKE ? AND deleted = 0",
                newPrefix, oldPrefix.length() + 1, oldPrefix + "%");
        auditService.log("promoter.parent.change", BIZ_TYPE, id, reason, me.getParentId(), newParentId);
    }

    @Transactional
    public void freeze(Long id, String reason) {
        requireReason(reason);
        transition(id, ST_FROZEN, "promoter.freeze", reason, ST_NORMAL);
    }

    @Transactional
    public void unfreeze(Long id) {
        transition(id, ST_NORMAL, "promoter.unfreeze", null, ST_FROZEN);
    }

    @Transactional
    public void audit(Long id, boolean pass, String reason) {
        if (pass) {
            transition(id, ST_NORMAL, "promoter.audit.pass", reason, ST_PENDING);
        } else {
            requireReason(reason);
            transition(id, ST_EXITED, "promoter.audit.reject", reason, ST_PENDING);
        }
    }

    /** 退出:份额园区留存、不向上补(引擎里按 status=4 处理);手机号/openid 匿名化由注销接口做,这里只改状态。 */
    @Transactional
    public void exit(Long id, String reason) {
        requireReason(reason);
        transition(id, ST_EXITED, "promoter.exit", reason, ST_NORMAL, ST_FROZEN);
    }

    @Transactional
    public String resetInvite(Long id) {
        MktPromoter p = require(id);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = randomInvite();
            try {
                promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                        .eq(MktPromoter::getId, id).set(MktPromoter::getInviteCode, code));
                auditService.log("promoter.invite.reset", BIZ_TYPE, id, null, p.getInviteCode(), code);
                return code;
            } catch (DuplicateKeyException ignored) {
                // 撞码重试
            }
        }
        throw new BizException("重置邀请码失败,请重试");
    }

    public boolean isInternal(String phone) {
        return StringUtils.hasText(phone)
                && sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0;
    }

    static String randomInvite() {
        StringBuilder sb = new StringBuilder(INVITE_LEN);
        for (int i = 0; i < INVITE_LEN; i++) {
            sb.append(INVITE_ALPHABET.charAt(RND.nextInt(INVITE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private void transition(Long id, int to, String action, String reason, Integer... from) {
        int updated = promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, id)
                .in(MktPromoter::getStatus, (Object[]) from)
                .set(MktPromoter::getStatus, to));
        if (updated == 0) {
            throw new BizException("伙伴 " + id + " 状态已变化,请刷新后重试");
        }
        auditService.log(action, BIZ_TYPE, id, reason);
    }

    private MktPromoter require(Long id) {
        MktPromoter p = promoterMapper.selectById(id);
        if (p == null) {
            throw new BizException("伙伴不存在: " + id);
        }
        return p;
    }

    private static void requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("必须填写原因");
        }
    }
}
