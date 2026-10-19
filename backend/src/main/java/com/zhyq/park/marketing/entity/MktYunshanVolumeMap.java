package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 园区客户与云山实体货主、实体仓库的固定映射；停用保留历史。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_yunshan_volume_map")
public class MktYunshanVolumeMap extends BaseEntity {
    private Long customerId;
    private Long warehouseId;
    private Long operationWarehouseId;
    private String operationWarehouseName;
    private String connectionBaseUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String connectionAppIdEnc;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String connectionPublicKeyEnc;
    private String parentOwnerNo;
    private String parentOwnerName;
    private String parentWarehouseNo;
    private String parentWarehouseName;
    private Integer status;
    private LocalDateTime lastSyncAt;
    private Integer lastSyncOk;
    private Long projectId;
}
