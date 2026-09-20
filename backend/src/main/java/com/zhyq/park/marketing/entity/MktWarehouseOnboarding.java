package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 云仓加盟步骤(表 crm_warehouse_onboarding,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_warehouse_onboarding")
public class MktWarehouseOnboarding extends BaseEntity {
    private Long warehouseId;
    /** 1申请 2资质审核 3ERP打通 4签协议 5上线 */
    private Integer step;
    /** 0待处理 1进行中 2通过 3驳回 */
    private Integer status;
    /** 负责人 sys_user.id */
    private Long ownerId;
    private LocalDate deadline;
    private LocalDateTime doneTime;
    private String rejectReason;
    private String attachments;
    private Long projectId;
}
