package com.zhyq.park.rsv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 统一资源目录(rsv_resource,#23)。会议室/场地/工位统一成一张目录表。
 * type: MEETING/SITE/DESK;status: 1可用 0停用。
 * 命名用 ResvResource 前缀避开 Spring/Jakarta Resource 类歧义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rsv_resource")
public class ResvResource extends BaseEntity {

    private String type;
    private String name;
    private Long spaceId;
    private Integer capacity;
    private BigDecimal pricePerHour;
    private Integer status;
    private String remark;
}
