package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_article")
public class Article extends BaseEntity {
    private String title;
    private String category;
    private String author;
    private String content;
    private Integer views;
    private Integer status;
}
