package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_lease_contract")
public class LeaseContract extends BaseEntity {

    private Integer seqNo;
    private Long projectId;
    private Long tenantRefId;
    private String tenantName;
    private String unitName;
    private String agreementNo;
    private BigDecimal leaseArea;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalMonths;
    private String pricingMode;
    private BigDecimal rentUnitPrice;
    /** 月固定总价(元/月,可选;填写后起始租金与后续递增均以此为基数) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal fixedMonthlyRent;
    private BigDecimal propertyUnitPrice;
    private BigDecimal escalationRate;
    private Integer escalationMonths;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal rentDeposit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal propertyDeposit;
    private String collectionTiming;
    private String depositTerms;
    /** 租金保证金缴纳状态: 0未缴纳 1部分缴纳 2已缴纳 */
    private Integer rentDepositStatus;
    private BigDecimal rentDepositPaid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate rentDepositPaidDate;
    /** 物业保证金缴纳状态: 0未缴纳 1部分缴纳 2已缴纳 */
    private Integer propertyDepositStatus;
    private BigDecimal propertyDepositPaid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate propertyDepositPaidDate;
    private String depositRemark;
    private String rulesJson;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private Integer rentReceiptCount;
    @TableField(exist = false)
    private Integer propertyReceiptCount;
}
