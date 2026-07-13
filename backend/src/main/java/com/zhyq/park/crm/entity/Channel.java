package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_channel")
public class Channel extends BaseEntity {
    private String name;
    private String contact;
    private String phone;
    /** 佣金比例% */
    private BigDecimal commissionRate;
    private Integer status;
    private String remark;
}
