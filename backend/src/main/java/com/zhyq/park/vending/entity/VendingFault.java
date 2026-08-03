package com.zhyq.park.vending.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_vending_fault")
public class VendingFault extends BaseEntity {
    private String vendorFaultId;
    private String vendorMachineId;
    private String faultType;
    private LocalDateTime occurredTime;
    private LocalDateTime recoveredTime;
    private String faultStatus;
    private String description;
    private Long sourceBatchId;
}
