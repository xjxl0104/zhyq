package com.zhyq.park.suggestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("suggestion_image")
public class SuggestionImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long suggestionId;
    private Long fileId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
