package com.zhyq.park.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable_register")
public class ReceivableRegister extends BaseEntity {
    private String internalCode;
    private String businessKey;
    private Integer seqNo;
    private String agreementNoRaw;
    private String tenantNameRaw;
    private String spaceNameRaw;
    private BigDecimal chargeArea;
    private BigDecimal actualArea;
    private BigDecimal sharedArea;
    private String contractTermRaw;
    private BigDecimal contractRentTotal;
    private String contractPeriodRaw;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String escalationRaw;
    private String freeTermRaw;
    private String freePeriodRaw;
    private String discountRaw;
    private String rentRateRaw;
    private String propertyRateRaw;
    private BigDecimal monthlyRent;
    private BigDecimal monthlyProperty;
    private BigDecimal monthlyTotal;
    private BigDecimal rentDeposit;
    private BigDecimal propertyDeposit;
    private String collectionTimingRaw;
    private String firstCollectionRaw;
    private Long rentAccountId;
    private Long propertyAccountId;
    private String rentAccountMasked;
    private String propertyAccountMasked;
    private String notesRaw;
    private BigDecimal depositDifference;
    private Long tenantRefId;
    private Long spaceId;
    private Long roomId;
    private Long contractId;
    private String status;
    private Long sourceBatchId;
    private Long sourceRowId;
    private Integer sourceVersion;
    /** 列表展示字段:该登记已生成的账单数(登记表是账单源头,生成进度要看得见) */
    @TableField(exist = false)
    private Integer billCount;
}
