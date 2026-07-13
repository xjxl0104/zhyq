package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_product")
public class Product extends BaseEntity {
    private String name;
    private String productType;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private Integer status;
}
