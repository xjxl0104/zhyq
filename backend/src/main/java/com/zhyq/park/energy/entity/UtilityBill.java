package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 月度公用事业账单(对外发票口径)。
 *
 * <p>园区对外只有一张电费/水费发票,分摊的全部分母都来自它:总用量、不含税总额、税率。
 * 一个园区、一种能源、一个账期只允许一张有效账单(库里唯一索引 uk_utility_bill_active)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_utility_bill")
public class UtilityBill extends BaseEntity {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_CONFIRMED = "CONFIRMED";

    private Long projectId;
    /** 电/水 */
    private String energyType;
    /** 账期 yyyy-MM */
    private String period;
    /** 发票总用量 */
    private BigDecimal invoiceUsage;
    /** 发票不含税总额 */
    private BigDecimal invoiceAmountExTax;
    /** 税率% */
    private BigDecimal taxRate;
    private String status;
    private String remark;

    /** 生成列,只读 */
    @TableField(exist = false)
    private String activeKey;
}
