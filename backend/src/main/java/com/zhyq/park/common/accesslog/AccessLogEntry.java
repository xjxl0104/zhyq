package com.zhyq.park.common.accesslog;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AccessLogEntry {
    private Long userId;
    private Long deptId;
    private String route;
    private String method;
    private String module;
    private Boolean isCore;
    private Integer statusCode;
    private Integer durationMs;
    private LocalDateTime createdAt = LocalDateTime.now();
}
