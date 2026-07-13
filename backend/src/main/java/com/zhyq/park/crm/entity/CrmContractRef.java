package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 合同精简映射(biz_contract),仅供招商佣金计算跨模块只读使用,避免依赖 contract 包。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract")
public class CrmContractRef extends BaseEntity {
    private String code;
    /** 租赁单价(元/㎡/月) */
    private BigDecimal rentPrice;
    /** 租赁面积 */
    private BigDecimal rentArea;
    /** 合同状态,5=执行中 */
    private Integer status;
}
