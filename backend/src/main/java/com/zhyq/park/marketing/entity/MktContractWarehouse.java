package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 合同 ↔ 承接云仓(可多仓、可换仓)(表 crm_contract_warehouse,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contract_warehouse")
public class MktContractWarehouse extends BaseEntity {
    private Long contractId;
    private Long warehouseId;
    private Integer isPrimary;
    private LocalDate effectiveFrom;
    /** 换仓时旧仓写切换日 */
    private LocalDate effectiveTo;
    private Long projectId;
}
