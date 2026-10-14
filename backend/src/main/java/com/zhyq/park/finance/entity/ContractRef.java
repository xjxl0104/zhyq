package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同精简映射(biz_contract),供财务退房报表与合同收款全景跨模块只读使用,避免依赖 contract 包。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract")
public class ContractRef extends BaseEntity {

    private String code;
    private Long tenantRefId;
    private Integer status;
    /** 合同类型:7 为合作合同 */
    private Integer contractType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal deposit;
    /** 退租时间 */
    private LocalDate terminateDate;
}
