package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_periodic_task_log")
public class PeriodicTaskLog extends BaseEntity {
    private Long taskId;
    private Integer yearNo;
    private Integer monthNo;
    /** 0待执行 1进行中 2已完成 3异常/逾期 */
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate plannedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate completedDate;
    private String executor;
    private String remark;

    @TableField(exist = false)
    private Integer attachmentCount;
}
