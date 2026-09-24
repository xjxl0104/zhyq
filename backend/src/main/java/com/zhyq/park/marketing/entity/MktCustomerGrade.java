package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 客户评级与总比例(表 crm_customer_grade,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_grade")
public class MktCustomerGrade extends BaseEntity {
    /** A/B/C/D */
    private String code;
    private String name;
    private String descr;
    /** 园区入驻:佣金池 = 月租金 × 本值(0.25–2) */
    private BigDecimal leaseCommissionMonths;
    /** 客户入仓:出库单/平台费的总比例% */
    private BigDecimal erpTotalRate;
    /** 增值服务总比例%(预留) */
    private BigDecimal serviceTotalRate;
    /** 入仓合同签约奖(固定额,可为 0) */
    private BigDecimal contractBonus;
    /** 评级自动建议:租赁线月租金下限 */
    private BigDecimal autoMinRent;
    /** 评级自动建议:云仓线预计月出库单量下限 */
    private Integer autoMinOrders;
    private Integer sort;
    private Integer status;
    private Long projectId;
}
