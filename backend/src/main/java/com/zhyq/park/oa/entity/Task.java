package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_task")
public class Task extends BaseEntity {
    private String title;
    private String owner;
    private Integer priority;
    private LocalDateTime dueDate;
    private Integer status;
    private String source;
    private String content;
}
