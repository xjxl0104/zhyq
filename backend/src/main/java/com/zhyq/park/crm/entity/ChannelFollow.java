package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 中介跟进记录,一个中介对应多行。新增后由 ChannelFollowService 回写中介的跟进统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_channel_follow")
public class ChannelFollow extends BaseEntity {

    /** 跟进编号 ZF-0001,服务端生成 */
    private String followNo;
    private Long channelId;
    private LocalDate followDate;
    /** 跟进方式:电话/微信·在线沟通/上门拜访/带客看仓/邮件/其他 */
    private String type;
    private String followBy;
    /** 沟通要点 */
    private String content;
    /** 本次推荐客户/房源需求 */
    private String referralClient;
    /** 合作意向变化:明显升温/持平/降温/暂停合作 */
    private String coopChange;
    private LocalDateTime nextFollow;
    /** 跟进结果/待办 */
    private String result;
}
