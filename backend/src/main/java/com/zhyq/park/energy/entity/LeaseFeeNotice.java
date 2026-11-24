package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_lease_fee_notice")
public class LeaseFeeNotice extends BaseEntity {

    public static final String CAT_RENT = "RENT";
    public static final String CAT_PROPERTY = "PROPERTY";

    private Long leasePeriodId;
    private String period;
    /** RENT=租金, PROPERTY=物业管理费(收取单位不同,分别出单) */
    private String feeCategory;
    private String noticeNo;
    private String payeeName;
    private String paymentAccount;
    private Long tenantRefId;
    private String tenantName;
    private String unitName;
    private String agreementNo;
    private BigDecimal leaseArea;
    private BigDecimal standardAmount;
    private BigDecimal payableAmount;
    private BigDecimal otherFee;
    private BigDecimal subtotal;
    private BigDecimal paidAmount;
    private BigDecimal priorArrears;
    private BigDecimal totalDue;
    private Long billId;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer paymentStatus;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer invoiceStatus;
    private Integer sort;
    private String remark;

    @TableField(exist = false)
    private List<LeaseFeeNoticeItem> items;
}
