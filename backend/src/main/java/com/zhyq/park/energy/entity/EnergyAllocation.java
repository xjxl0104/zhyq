package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 公用事业费分摊结果:一行 = 某账期某块分表该付多少。
 * 字段与《附件二》的四个公式一一对应,便于对账时逐步核。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_allocation")
public class EnergyAllocation extends BaseEntity {

    private Long utilityBillId;
    private String period;
    private String energyType;
    private Long meterId;
    private String meterRole;
    private Long tenantRefId;
    /** 自身抄表用量 */
    private BigDecimal ownUsage;
    /** 分摊系数(公式②) */
    private BigDecimal allocCoefficient;
    /** 分摊用量 = 自身用量 × 系数 */
    private BigDecimal allocUsage;
    /** 当月不含税单价(公式①) */
    private BigDecimal unitPriceExTax;
    private BigDecimal taxRate;
    /** 自身费用 = 自身用量 × 单价 × (1+税率) */
    private BigDecimal ownFee;
    /** 公摊费用(公式③) */
    private BigDecimal allocFee;
    /** 总费用(公式④) */
    private BigDecimal totalFee;
    /** 出账后指向 fin_bill */
    private Long billId;

    // ---- 展示字段(不落库)
    @TableField(exist = false)
    private String meterCode;
    @TableField(exist = false)
    private String meterName;
    @TableField(exist = false)
    private String tenantName;
    @TableField(exist = false)
    private String billCode;
}
