package com.zhyq.park.suggestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("suggestion_log")
public class SuggestionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long suggestionId;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
}
