package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商合同(V53)。
 *
 * <p>与租赁合同 biz_contract 分表:后者 tenant_ref_id 必填、字段是租赁语义
 * (租金单价/面积/免租月/房源关联),本表是采购服务语义(合同金额/结算周期/账期)。
 *
 * <p>状态 1草稿 2执行中 3已到期 4已终止,流转一律条件更新抢状态(项目硬约束)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_supplier_contract")
public class SupplierContract extends BaseEntity {

    /** 合同编号 GYSHT-2026-0001,服务端生成 */
    private String code;

    private Long supplierId;

    /** 合同名称,如 2026年度保洁服务合同 */
    private String name;

    /** 合同类型:字典 supplier_contract_type 的 value */
    private String contractType;

    private BigDecimal amount;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate signDate;

    /** 结算周期:月结/季结/一次性等 */
    private String payCycle;

    /** 付款方式/账期说明 */
    private String payTerms;

    /** 1草稿 2执行中 3已到期 4已终止 */
    private Integer status;

    private String remark;

    /** 列表展示用:供应商名称,由控制器回填,不落库 */
    @TableField(exist = false)
    private String supplierName;
}
