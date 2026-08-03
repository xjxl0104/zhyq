package com.zhyq.park.vending.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_vending_sale")
public class VendingSale extends BaseEntity {
    private String vendorOrderId;
    private Integer lineNo;
    private String vendorMachineId;
    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private String paymentMethod;
    private LocalDateTime paymentTime;
    private String orderStatus;
    private Long sourceBatchId;
}
