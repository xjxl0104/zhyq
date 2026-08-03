package com.zhyq.park.vending.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_vending_restock")
public class VendingRestock extends BaseEntity {
    private String vendorRestockId;
    private String vendorMachineId;
    private String productId;
    private String productName;
    private Integer quantity;
    private String operatorName;
    private LocalDateTime restockTime;
    private Long sourceBatchId;
}
