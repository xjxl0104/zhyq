package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购计划(pur_plan)。planType:1年度 2月度 3临时。status:1草稿 2生效 3已完成 4已关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_plan")
public class PurPlan extends BaseEntity {

    private String planNo;
    private String title;
    private Integer planType;
    private String period;
    private String department;
    private String applicant;
    private BigDecimal budgetAmount;
    private Integer status;
    private String remark;
}
