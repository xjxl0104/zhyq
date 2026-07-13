package com.zhyq.park.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_app_favorite")
public class AppFavorite extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 应用ID */
    private Long appId;
}
