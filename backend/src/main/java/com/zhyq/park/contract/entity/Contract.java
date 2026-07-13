package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同(biz_contract)
 * 合同类型:1正式 2意向 3草稿 4电子 5优惠 6成本
 * 合同状态:1草稿 2待审核 3待签署 4待执行 5执行中 6变更中 7退租中 8已到期 9已终止 10已归档
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract")
public class Contract extends BaseEntity {

    /** 合同编号(全局唯一) */
    private String code;
    /** 租客 */
    private Long tenantRefId;
    private Long projectId;
    /** 合同类型 */
    private Integer contractType;
    /** 合同状态 */
    private Integer status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signDate;
    /** 租赁单价(元/㎡/月) */
    private BigDecimal rentPrice;
    /** 物业单价 */
    private BigDecimal propertyPrice;
    /** 租赁面积 */
    private BigDecimal rentArea;
    /** 保证金 */
    private BigDecimal deposit;
    /** 计费方式:1按面积单价 2固定金额 3阶梯单价 */
    private Integer chargeMode;
    /** 付款周期(月):1/3/6/12 */
    private Integer payCycle;
    /** 免租月数 */
    private Integer freeMonths;
    /** 年递增率% */
    private BigDecimal increaseRate;
    private String source;
    /** 退租时间 */
    private LocalDate terminateDate;
    private String remark;
}
