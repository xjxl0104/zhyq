package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 月度缴费账期(水电垃圾)。一个园区一个月一条。
 *
 * <p>装的是线下《应收收费明细表》里要人工填的那几个数:南网 / 光伏用电总度数、水总表度数、
 * 两种垃圾清运总额、单价,以及通知单抬头(联系人、收款账户、开具日期)。
 * 各户的分摊结果落在 {@link FeeNotice} / {@link FeeNoticeItem}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_fee_period")
public class FeePeriod extends BaseEntity {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_CONFIRMED = "CONFIRMED";

    private Long projectId;
    /** 账期 yyyy-MM */
    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private String parkName;
    private String noticePrefix;

    /** 南网用电总度数 */
    private BigDecimal electricGridUsage;
    /** 光伏发电园区用电度数 */
    private BigDecimal electricSolarUsage;
    /** 电费单价 元/度 */
    private BigDecimal electricPrice;
    /** 水总表度数;为空时按发票总表(MAIN)当月抄表取 */
    private BigDecimal waterTotalUsage;
    private BigDecimal waterPrice;

    private BigDecimal domesticGarbageAmount;
    private String domesticGarbageNote;
    private BigDecimal industrialGarbageAmount;
    private String industrialGarbageNote;

    private LocalDate issueDate;
    private LocalDate dueDate;
    private String contactName;
    private String contactPhone;
    private String paymentAccount;
    private String remark;

    // ---- 最近一次生成通知单的汇总快照
    private BigDecimal electricSubmeterUsage;
    private BigDecimal electricPublicUsage;
    private BigDecimal waterUsedTotal;
    private BigDecimal waterSubmeterUsage;
    private BigDecimal waterPublicUsage;
    private BigDecimal totalLeaseArea;
    private Integer noticeCount;
    private BigDecimal totalAmount;
    private LocalDateTime generatedTime;
    /** 最近一次改动读数/总量/表计的时间;晚于 generatedTime 说明通知单已过期 */
    private LocalDateTime inputsChangedTime;

    /** 通知单是否已过期(派生,不落库) */
    @TableField(exist = false)
    private Boolean noticeStale;

    /** 生成列,只读 */
    @TableField(exist = false)
    private String activeKey;
}
