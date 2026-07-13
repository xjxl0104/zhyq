package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 合同版本(biz_contract_version,变更/续租/退租留痕)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_version")
public class ContractVersion extends BaseEntity {

    private Long contractId;
    /** 版本序号 */
    private Integer versionNo;
    /** 变更类型:续租/扩租/缩租/换房/变更单价/变更账期/主体变更/退租 */
    private String changeType;
    /** 变更前快照(JSON) */
    private String snapshot;
    private LocalDate effectDate;
    private String remark;
}
