package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_plan")
public class Plan extends BaseEntity {
    private String title;
    private String owner;
    /** 周期,如 2026-Q3 */
    private String period;
    /** 目标签约额 */
    private BigDecimal targetAmount;
    /** 已达成 */
    private BigDecimal achievedAmount;
    /** 状态:1进行中 2已完成 */
    private Integer status;
    private String remark;
}
