package com.zhyq.park.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant_message")
public class TenantMessage extends BaseEntity {
    /** 标题 */
    private String title;
    /** 正文 */
    private String content;
    /** 接收租客(空=全部租客) */
    private Long tenantRefId;
    /** 渠道:站内信/短信/微信 */
    private String channel;
    /** 状态:1草稿 2已发送 */
    private Integer status;
    /** 0未读 1已读 */
    private Integer readFlag;
    /** 发送时间 */
    private LocalDateTime sendTime;
}
