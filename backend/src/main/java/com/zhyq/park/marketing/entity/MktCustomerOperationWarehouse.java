package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户与本地作业仓库关系；停用保留历史，不产生发货量或接口映射。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_operation_warehouse")
public class MktCustomerOperationWarehouse extends BaseEntity {
    private Long warehouseId;
    private Long customerId;
    private Long operationWarehouseId;
    private Integer status;
    private Long projectId;
}
