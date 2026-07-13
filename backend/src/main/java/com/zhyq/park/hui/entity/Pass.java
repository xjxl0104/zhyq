package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 物品放行(srv_pass)
 * 状态:1待核验 2已放行 3已失效
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_pass")
public class Pass extends BaseEntity {
    private String passNo;
    private String item;
    private Integer qty;
    private String carrier;
    private String phone;
    private String plateNo;
    private Long tenantRefId;
    private String authorizer;
    private LocalDateTime validUntil;
    private Integer status;
}
