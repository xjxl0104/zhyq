package com.zhyq.park.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable_rule")
public class ReceivableRule extends BaseEntity {
    private Long registerId;
    private String feeType;
    private String ruleType;
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private String rateUnit;
    private BigDecimal rateValue;
    private BigDecimal fixedAmount;
    private BigDecimal discountRate;
    private Integer intervalYears;
    private BigDecimal increaseRate;
    private String recurrenceRule;
    private String applyScope;
    private Integer priority;
    private String rawText;
    private String status;
}
