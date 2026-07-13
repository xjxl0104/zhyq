package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会议室(pm_meeting_room)
 * 状态:1可用 0停用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_meeting_room")
public class MeetingRoom extends BaseEntity {

    private String name;
    private Long projectId;
    private Integer capacity;
    private String equipment;
    /** 开放时间 */
    private String openTime;
    /** 计费规则 */
    private String feeRule;
    private Integer status;
}
