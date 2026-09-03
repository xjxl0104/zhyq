package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账单(财务模块)。
 * 注:合同等其它模块可各自建映射 fin_bill 的实体,MyBatis-Plus 允许多实体映射同表。
 * 状态:1草稿 2待审核 3待收付 4部分结清 5已结清 6逾期 7退款中 8作废。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_bill")
public class Bill extends BaseEntity {

    /** 账单号(全局唯一) */
    private String code;
    /** 计费幂等键 */
    private String billingKey;
    /** 来源合同 */
    private Long contractId;
    /** 来源应收登记表 */
    private Long receivableRegisterId;
    /** 来源计费规则 */
    private Long receivableRuleId;
    /** 租客 */
    private Long tenantRefId;
    /** 账单查询展示字段：优先取权威应收登记中的租客名称。 */
    @TableField(exist = false)
    private String tenantName;
    /** 账单查询展示字段：来源登记明细中的协议编号。 */
    @TableField(exist = false)
    private String agreementNo;
    private Long projectId;
    private Long buildingId;
    private Long roomId;
    /** 方向:1收款 2付款 */
    private Integer direction;
    /** 费用类型:租金/物业费/保证金/能源费/服务费/一次性 */
    private String feeType;
    /** 来源:合同计划/抄表/人工/商城/预约/工单/接口 */
    private String source;
    /** 账单状态(附录B) */
    private Integer status;
    /** 应收/应付金额 */
    private BigDecimal amount;
    /** 实收金额 */
    private BigDecimal paidAmount;
    /** 滞纳金 */
    private BigDecimal lateFee;
    /** 滞纳金人工调整标记:1=锁定,LateFeeService 自动重算整条跳过 */
    private Integer lateFeeManual;
    private String lateFeeRemark;
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
