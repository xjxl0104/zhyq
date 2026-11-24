package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 缴费通知单:一户(租户 × 楼层单元)一张,对应线下表里每个租户的那一个 sheet。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_fee_notice")
public class FeeNotice extends BaseEntity {

    private Long feePeriodId;
    private String period;
    private String noticeNo;
    private Long tenantRefId;
    private String tenantName;
    /** 厂房/单元,如 2层A~C区 */
    private String unitName;
    private String floorKey;
    private BigDecimal leaseArea;
    /** 1=参与公摊与垃圾分摊 0=只付自身用量(充电桩/未租) */
    private Integer sharePublic;

    private BigDecimal waterUsage;
    private BigDecimal waterPublicUsage;
    private BigDecimal waterFee;
    private BigDecimal waterPublicFee;
    private BigDecimal electricUsage;
    private BigDecimal electricPublicUsage;
    private BigDecimal electricFee;
    private BigDecimal electricPublicFee;
    private BigDecimal domesticGarbageFee;
    private BigDecimal industrialGarbageFee;
    private BigDecimal otherFee;
    private String otherFeeNote;
    /** 费用小计 */
    private BigDecimal subtotal;
    private BigDecimal paidAmount;
    /** 缴费登记状态：0=未缴费，1=已缴费。独立于费用计算，只由缴费接口显式更新。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer paymentStatus;
    /** 开票登记状态：0=未开票，1=已开票。独立于费用计算，只由发票接口显式更新。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer invoiceStatus;
    private BigDecimal priorArrears;
    /** 本期应收合计 = 小计 − 已付 + 前期欠费 */
    private BigDecimal totalDue;
    private Long billId;
    private Integer sort;
    private String remark;

    @TableField(exist = false)
    private List<FeeNoticeItem> items = new ArrayList<>();
}
