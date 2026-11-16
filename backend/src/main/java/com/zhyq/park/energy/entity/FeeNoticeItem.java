package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 通知单明细行,与纸质通知单的表格一行一行对应:
 * 序号 / 费用项目 / 上期读数 / 本期读数 / 计费数量 / 单位 / 单价 / 金额 / 备注。
 *
 * <p>itemType 以 _DETAIL 结尾的是分表子行(一户有多块电表时逐表列出读数),
 * 金额为空、不计入合计,与线下表「3.1 30倍电表 / 3.2 80倍电表」的写法一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_fee_notice_item")
public class FeeNoticeItem extends BaseEntity {

    public static final String T_WATER = "WATER";
    public static final String T_WATER_DETAIL = "WATER_DETAIL";
    public static final String T_WATER_PUBLIC = "WATER_PUBLIC";
    public static final String T_ELECTRIC = "ELECTRIC";
    public static final String T_ELECTRIC_DETAIL = "ELECTRIC_DETAIL";
    public static final String T_ELECTRIC_PUBLIC = "ELECTRIC_PUBLIC";
    public static final String T_GARBAGE_DOMESTIC = "GARBAGE_DOMESTIC";
    public static final String T_GARBAGE_INDUSTRIAL = "GARBAGE_INDUSTRIAL";
    public static final String T_OTHER = "OTHER";

    private Long noticeId;
    private Long feePeriodId;
    private String seq;
    private String itemType;
    private String feeItem;
    private Long meterId;
    private String meterCode;
    private BigDecimal ratio;
    private BigDecimal prevReading;
    private BigDecimal currReading;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    private Integer sort;

    public boolean isDetail() {
        return itemType != null && itemType.endsWith("_DETAIL");
    }
}
