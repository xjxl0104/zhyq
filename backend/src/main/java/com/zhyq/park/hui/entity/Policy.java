package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_policy")
public class Policy extends BaseEntity {
    private String title;
    private String source;
    private String ptype;
    private String industry;
    private LocalDate publishDate;
    private LocalDate deadline;
    private String content;
    private Integer status;
}
