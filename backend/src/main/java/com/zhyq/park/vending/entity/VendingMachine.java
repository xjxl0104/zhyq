package com.zhyq.park.vending.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_vending_machine")
public class VendingMachine extends BaseEntity {
    private String vendorMachineId;
    private String machineName;
    private String siteName;
    private String model;
    private String runningStatus;
    private LocalDateTime lastOnlineTime;
    private Long sourceBatchId;
}
