package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账单(fin_bill)——本模块合同生效时写入账单计划使用。
 * 方向:1收款 2付款
 * 费用类型:租金/物业费/保证金/能源费/服务费/一次性
 * 状态:1草稿 2待审核 3待收付 4部分结清 5已结清 6逾期 7退款中 8作废
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_bill")
public class FinBill extends BaseEntity {

    /** 账单号(全局唯一) */
    private String code;
    /** 来源合同 */
    private Long contractId;
    /** 租客 */
    private Long tenantRefId;
    private Long projectId;
    private Long buildingId;
    private Long roomId;
    /** 方向:1收款 2付款 */
    private Integer direction;
    /** 费用类型 */
    private String feeType;
    /** 来源:合同计划/抄表/人工/商城/预约/工单/接口 */
    private String source;
    /** 账单状态 */
    private Integer status;
    /** 应收/应付金额 */
    private BigDecimal amount;
    /** 实收金额 */
    private BigDecimal paidAmount;
    /** 滞纳金 */
    private BigDecimal lateFee;
    /** 税率% */
    private BigDecimal taxRate;
    /** 账期起 */
    private LocalDate periodStart;
    /** 账期止 */
    private LocalDate periodEnd;
    /** 应收日 */
    private LocalDate dueDate;
    /** 逾期天数 */
    private Integer overdueDays;
    /** 0未开 1已开 */
    private Integer invoiceStatus;
    private String remark;
}
