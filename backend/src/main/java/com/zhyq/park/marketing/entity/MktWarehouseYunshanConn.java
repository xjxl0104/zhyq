package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 云仓级云山发货量接口连接：同一云仓下所有客户共用；凭证加密保存，不随接口返回。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_warehouse_yunshan_conn")
public class MktWarehouseYunshanConn extends BaseEntity {
    private Long warehouseId;
    private String baseUrl;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String appIdEnc;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String publicKeyEnc;
    private Long projectId;
}
