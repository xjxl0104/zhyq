package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 计佣事件(表 crm_referral_order,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_referral_order")
public class MktReferralOrder extends BaseEntity {
    /** 1租赁签约(一次性) 2出库单 3平台费收款 4签约奖 5增值服务收款 */
    private Integer sourceType;
    /** 业务单号:LEASE-{合同id} / 出库单号 / … */
    private String sourceNo;
    /** 拆单子单的父单号 */
    private String parentOrderNo;
    /** 来源记录 id(biz_contract.id / crm_service_contract.id) */
    private Long sourceId;
    private Long warehouseId;
    /** 成交伙伴 */
    private Long promoterId;
    private Long customerId;
    /** 货主在云仓 ERP 的编码(阶段 C) */
    private String customerCode;
    /** 评级快照 */
    private String customerGrade;
    /** 租赁=佣金月数;入仓=总比例% */
    private BigDecimal poolFactor;
    /** 1园区服务费 2固定每单 3货值 4月租金 */
    private Integer baseMode;
    /** 计佣基数:月租金 / 园区服务费 / 平台费 */
    private BigDecimal baseAmount;
    /** 佣金池 = 基数 × pool_factor */
    private BigDecimal poolAmount;
    private Integer qty;
    private Integer packages;
    /** 园区按合同单价表自算的服务费 */
    private BigDecimal serviceFee;
    /** ERP 传来的货值,仅参考 */
    private BigDecimal goodsAmount;
    private String logisticsNo;
    /** 1待确认 2已确认 3已退款 4已取消 5无归属 */
    private Integer status;
    private LocalDateTime eventTime;
    private LocalDateTime confirmTime;
    private String rawPayload;
    private String remark;
    private Long projectId;
}
