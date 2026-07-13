package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同精简映射(biz_contract),仅供财务退房报表跨模块只读使用,避免依赖 contract 包。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract")
public class ContractRef extends BaseEntity {

    private String code;
    private Long tenantRefId;
    private Integer status;
    private BigDecimal deposit;
    /** 退租时间 */
    private LocalDate terminateDate;
}
