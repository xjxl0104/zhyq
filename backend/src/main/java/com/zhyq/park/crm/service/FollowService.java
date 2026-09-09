package com.zhyq.park.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Follow;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.FollowMapper;
import com.zhyq.park.crm.mapper.LeadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 跟进记录服务:发跟进编号 + 回写线索的跟进统计。
 *
 * <p>登记表里「最近跟进日期」「累计跟进次数」是两列自动公式,这里对应成新增跟进后回写线索。
 * 回写与插入同事务:统计对不上比没有统计更糟。</p>
 *
 * <p>次数用 SQL 原子累加(follow_count = follow_count + 1),不做「先查后改」——
 * 两人同时给一条线索加跟进时,读改写会丢掉一次。</p>
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private static final String NO_PREFIX = "GF-";

    private final FollowMapper followMapper;
    private final LeadMapper leadMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(Follow follow) {
        if (follow.getLeadId() == null) {
            throw new BizException("缺少所属线索");
        }
        if (leadMapper.selectById(follow.getLeadId()) == null) {
            throw new BizException("线索不存在或已删除");
        }
        follow.setId(null);
        follow.setFollowNo(nextFollowNo());
        if (follow.getFollowDate() == null) {
            follow.setFollowDate(LocalDate.now());
        }
        followMapper.insert(follow);

        // 回写一:次数原子累加(SQL 里没有任何入参,不存在拼接风险)。
        // 顺带把线索的下次跟进计划同步成本次填的,列表里「下次跟进」才是最新的那条。
        LambdaUpdateWrapper<Lead> uw = new LambdaUpdateWrapper<Lead>()
                .eq(Lead::getId, follow.getLeadId())
                .setSql("follow_count = follow_count + 1");
        if (follow.getNextFollow() != null) {
            uw.set(Lead::getNextFollow, follow.getNextFollow());
        }
        leadMapper.update(null, uw);

        // 回写二:最近跟进日期只在本次更晚时才推进 —— 补录一条早于已有记录的跟进不该把日期改回去。
        // 用条件更新表达,参数走占位符,不拼 SQL。
        leadMapper.update(null, new LambdaUpdateWrapper<Lead>()
                .eq(Lead::getId, follow.getLeadId())
                .and(w -> w.isNull(Lead::getLastFollowDate)
                           .or().lt(Lead::getLastFollowDate, follow.getFollowDate()))
                .set(Lead::getLastFollowDate, follow.getFollowDate()));
        return follow.getId();
    }

    /** 下一个跟进编号:GF- + 4 位顺序号。 */
    String nextFollowNo() {
        Follow last = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                .isNotNull(Follow::getFollowNo)
                .likeRight(Follow::getFollowNo, NO_PREFIX)
                .orderByDesc(Follow::getFollowNo)
                .last("limit 1"));
        return NO_PREFIX + String.format("%04d", parseSeq(last == null ? null : last.getFollowNo()) + 1);
    }

    static int parseSeq(String followNo) {
        if (followNo == null || !followNo.startsWith(NO_PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(followNo.substring(NO_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
