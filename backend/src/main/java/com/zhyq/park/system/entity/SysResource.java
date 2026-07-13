package com.zhyq.park.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运营资源(轮播图/导航/公告位/活动位)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_resource")
public class SysResource extends BaseEntity {

    /** 类型:轮播图/导航/公告位/活动位 */
    private String rtype;
    private String title;
    private String imageUrl;
    /** 跳转地址 */
    private String link;
    private Integer sort;
    /** 1上架 0下架 */
    private Integer status;
}
