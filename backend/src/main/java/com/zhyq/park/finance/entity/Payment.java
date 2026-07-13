package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付/收款单。pay_no 唯一,作为收款幂等键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class Payment extends BaseEntity {

    /** 支付流水号(幂等键) */
    private String payNo;
    private Long billId;
    private BigDecimal amount;
    /** 现金/转账/POS/微信/支付宝/聚合 */
    private String payMethod;
    private LocalDateTime payTime;
    private String operator;
    private String remark;
}
