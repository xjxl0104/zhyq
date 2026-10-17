package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户 ↔ 云仓货主编码(表 crm_customer_erp_map,V59) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_erp_map")
public class MktCustomerErpMap extends BaseEntity {
    private Long customerId;
    /** 展示当前客户名称，关联仍使用客户 ID；不写入映射表。 */
    @TableField(exist = false)
    private String customerName;
    private Long warehouseId;
    /** 货主在该云仓 ERP 里的编码,订单归属只认它(§2.6 v6 修正) */
    private String customerCode;
    private Integer status;
    private Long projectId;
}
