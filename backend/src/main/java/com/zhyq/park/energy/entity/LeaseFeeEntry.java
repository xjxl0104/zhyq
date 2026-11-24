package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_lease_fee_entry")
public class LeaseFeeEntry extends BaseEntity {

    private Long leasePeriodId;
    private String period;
    private Long contractId;
    private BigDecimal rentFactor;
    private BigDecimal propertyFactor;
    private BigDecimal rentAmountOverride;
    private BigDecimal propertyAmountOverride;
    private String remark;

    @TableField(exist = false)
    private String activeKey;
}
