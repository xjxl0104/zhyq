package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 加盟云仓自行维护的作业仓库档案，不代表任何外部接口授权。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_operation_warehouse")
public class MktOperationWarehouse extends BaseEntity {
    private Long warehouseId;
    private String name;
    private String address;
    private Integer status;
    private Long projectId;
}
