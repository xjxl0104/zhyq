package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_follow")
public class Follow extends BaseEntity {
    private Long leadId;
    /** 方式:电话/拜访/微信/邮件/会议 */
    private String type;
    private String content;
    private String followBy;
    private LocalDateTime nextFollow;
}
