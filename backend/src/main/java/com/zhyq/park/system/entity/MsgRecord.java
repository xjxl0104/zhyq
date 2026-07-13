package com.zhyq.park.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息发送记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_msg_record")
public class MsgRecord extends BaseEntity {

    private String templateCode;
    private String receiver;
    private String channel;
    private String content;
    /** 1成功 2失败 */
    private Integer status;
    private String failReason;
    private LocalDateTime sendTime;
}
