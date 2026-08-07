package com.zhyq.park.suggestion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("suggestion")
public class Suggestion extends BaseEntity {
    private String title;
    private String content;
    private Integer type;
    private String module;
    private String sourceUrl;
    private String userAgent;
    private Long userId;
    private Long deptId;
    private Integer status;
    private Integer priority;
    private Long assigneeId;
    private String closeReason;
    private LocalDateTime resolvedAt;
}
