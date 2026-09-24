package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 云仓服务合同模板(表 crm_contract_template,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contract_template")
public class MktContractTemplate extends BaseEntity {
    private String name;
    /** 1仓储 2代发 3仓配 */
    private Integer serviceType;
    /** 模板版本号(基座 version 是乐观锁,不混用) */
    private Integer tplVersion;
    private String body;
    private String variables;
    private Integer status;
    private Long projectId;
}
