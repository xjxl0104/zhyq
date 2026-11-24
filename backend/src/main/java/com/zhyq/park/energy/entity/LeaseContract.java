package com.zhyq.park.energy.entity;

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
    private BigDecimal propertyUnitPrice;
    private BigDecimal escalationRate;
    private Integer escalationMonths;
    private BigDecimal rentDeposit;
    private BigDecimal propertyDeposit;
    private String collectionTiming;
    private String depositTerms;
    private String rulesJson;
    private Integer status;
    private String remark;
}
