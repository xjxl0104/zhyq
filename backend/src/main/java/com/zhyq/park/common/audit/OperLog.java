package com.zhyq.park.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志(sys_oper_log)。由 {@link AuditAspect} 写入,不继承 BaseEntity(本表无租户/乐观锁/逻辑删列)。
 */
@Data
@TableName("sys_oper_log")
public class OperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;
    private String action;
    private String method;
    private String httpMethod;
    private String reqUri;
    private String params;
    private String operator;
    private String ip;
    private Integer success;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime createTime;
}
