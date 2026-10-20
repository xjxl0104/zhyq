package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** 不可变的每单定价版本，历史订单通过 pricingId 引用。contractId 非空=按合同分配版本，计佣优先于客户定价。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_pricing")
public class MktCustomerPricing extends BaseEntity {
    private Long customerId;
    /** 服务合同 crm_service_contract.id;非空=按合同分配版本(客户定价行为 NULL) */
    private Long contractId;
    private Long ownerPromoterId;
    private BigDecimal totalPerOrder;
    private BigDecimal ownerPerOrder;
    private String beneficiariesJson;
    private Long projectId;
}
