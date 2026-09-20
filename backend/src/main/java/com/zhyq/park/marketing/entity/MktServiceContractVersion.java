package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 云仓服务合同版本快照(表 crm_service_contract_version,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_service_contract_version")
public class MktServiceContractVersion extends BaseEntity {
    private Long contractId;
    private Integer verNo;
    private String snapshot;
    private String changedBy;
    private String changeNote;
    private Long projectId;
}
