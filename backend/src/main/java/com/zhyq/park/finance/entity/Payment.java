package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付/收款单。pay_no 唯一,作为收款幂等键。
 *
 * 撤销走红冲、不删记录:原单打 voidStatus=1,另生成一张 amount 为负、
 * originalPaymentId 指回原单的红冲单(voidStatus=2)。这样「谁在什么时候撤了哪一笔」
 * 永远查得到,账单实收的加减也能与支付单一一对上。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class Payment extends BaseEntity {

    /** 正常单 */
    public static final int VOID_NONE = 0;
    /** 已被撤销的原单 */
    public static final int VOID_ORIGINAL = 1;
    /** 红冲单(负额) */
    public static final int VOID_REVERSAL = 2;

    /** 支付流水号(幂等键) */
    private String payNo;
    private Long billId;
    private BigDecimal amount;
    /** 现金/转账/POS/微信/支付宝/聚合 */
    private String payMethod;
    /** 0=正常 1=已撤销的原单 2=红冲单 */
    private Integer voidStatus;
    private String voidReason;
    private LocalDateTime voidTime;
    private String voidBy;
    /** 红冲单指向的原支付单 id */
    private Long originalPaymentId;
    private LocalDateTime payTime;
    private String operator;
    private String remark;
}
