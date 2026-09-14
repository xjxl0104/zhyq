package com.zhyq.park.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.entity.ChannelFollow;
import com.zhyq.park.crm.mapper.ChannelFollowMapper;
import com.zhyq.park.crm.mapper.ChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 中介服务:发中介编号 / 跟进编号,新增跟进后回写中介的跟进统计。
 * 回写口径与线索的 FollowService 一致:次数 SQL 原子累加,最近跟进日期只前进不后退。
 */
@Service
@RequiredArgsConstructor
public class ChannelFollowService {

    private static final String AGENCY_PREFIX = "ZJ-";
    private static final String FOLLOW_PREFIX = "ZF-";

    private final ChannelMapper channelMapper;
    private final ChannelFollowMapper followMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(ChannelFollow follow) {
        if (follow.getChannelId() == null) {
            throw new BizException("缺少所属中介");
        }
        if (channelMapper.selectById(follow.getChannelId()) == null) {
            throw new BizException("中介不存在或已删除");
        }
        follow.setId(null);
        follow.setFollowNo(nextFollowNo());
        if (follow.getFollowDate() == null) {
            follow.setFollowDate(LocalDate.now());
        }
        followMapper.insert(follow);

        LambdaUpdateWrapper<Channel> uw = new LambdaUpdateWrapper<Channel>()
                .eq(Channel::getId, follow.getChannelId())
                .setSql("follow_count = follow_count + 1");
        if (follow.getNextFollow() != null) {
            uw.set(Channel::getNextFollow, follow.getNextFollow());
        }
        channelMapper.update(null, uw);

        channelMapper.update(null, new LambdaUpdateWrapper<Channel>()
                .eq(Channel::getId, follow.getChannelId())
                .and(w -> w.isNull(Channel::getLastFollowDate)
                           .or().lt(Channel::getLastFollowDate, follow.getFollowDate()))
                .set(Channel::getLastFollowDate, follow.getFollowDate()));
        return follow.getId();
    }

    /** 下一个中介编号:ZJ- + 4 位顺序号。 */
    public String nextAgencyNo() {
        return AGENCY_PREFIX + String.format("%04d", channelMapper.maxNoSeq(AGENCY_PREFIX) + 1);
    }

    String nextFollowNo() {
        return FOLLOW_PREFIX + String.format("%04d", followMapper.maxNoSeq(FOLLOW_PREFIX) + 1);
    }
}
