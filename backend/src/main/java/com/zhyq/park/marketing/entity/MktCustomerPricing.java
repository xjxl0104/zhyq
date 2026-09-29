package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** 不可变的客户每单定价版本，历史订单通过 pricingId 引用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_pricing")
public class MktCustomerPricing extends BaseEntity {
    private Long customerId;
    private Long ownerPromoterId;
    private BigDecimal totalPerOrder;
    private BigDecimal ownerPerOrder;
    private String beneficiariesJson;
    private Long projectId;
}
