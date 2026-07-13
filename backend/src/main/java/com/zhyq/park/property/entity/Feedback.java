package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投诉与意见反馈(pm_feedback)
 * 类型(ftype):投诉/意见
 * 状态:1待处理 2处理中 3已办结
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_feedback")
public class Feedback extends BaseEntity {

    /** 投诉/意见 */
    private String ftype;
    private String title;
    private String content;
    /** 提交租客 */
    private Long tenantRefId;
    private String contact;
    private String phone;
    /** 1待处理 2处理中 3已办结 */
    private Integer status;
    /** 处理回复 */
    private String reply;
    /** 处理人 */
    private String handler;
}
