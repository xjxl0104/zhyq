package com.zhyq.park.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 统一待办(sys_todo)
 * biz_type:contract/bill/workorder/lead/approval
 * 状态:1待办 2已读 3已完成
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_todo")
public class Todo extends BaseEntity {

    private String title;
    private String bizType;
    private Long bizId;
    private String owner;
    private LocalDateTime dueDate;
    private Integer status;
}
