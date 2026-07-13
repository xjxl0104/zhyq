package com.zhyq.park.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_msg_template")
public class MsgTemplate extends BaseEntity {

    /** 模板编码 */
    private String code;
    private String name;
    /** 渠道:站内信/短信/邮件 */
    private String channel;
    /** 内容,变量用{var} */
    private String content;
    /** 1启用 0停用 */
    private Integer status;
}
