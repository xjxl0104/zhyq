package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_forum_post")
public class ForumPost extends BaseEntity {
    private String title;
    private String content;
    private String author;
    private Long tenantRefId;
    private String category;
    private Integer replyCount;
    private Integer likeCount;
    private Integer status;
}
