package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 合同-房源关系(biz_contract_room,N:M)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_room")
public class ContractRoom extends BaseEntity {

    private Long contractId;
    private Long roomId;
    private BigDecimal rentArea;
    private BigDecimal rentPrice;
}
