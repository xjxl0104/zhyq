package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商档案(V53)。
 *
 * <p>与 iot 包的 Vendor(物联平台对接凭证)、property 包的 ResponsibleUnit(工单承接方)
 * 刻意分表:三者语义不同 —— 本表是采购/服务侧的供应商主数据,带资质与结算账户。
 *
 * <p>category 存的是字典 supplier_category 的 value,类别由使用方在
 * 「系统管理→字典管理」自行维护,后端不做枚举校验(加类别不必改代码)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_supplier")
public class Supplier extends BaseEntity {

    /** 供应商编号 GYS-0001,服务端生成 */
    private String code;

    private String name;

    /** 类别:字典 supplier_category 的 value */
    private String category;

    private String creditCode;

    private String legalPerson;

    private String contact;

    private String phone;

    private String email;

    private String regAddress;

    /** 开户行 */
    private String bankName;

    private String bankAccount;

    private String businessScope;

    /** 资质说明(营业执照等附件走通用 sys_file) */
    private String qualification;

    /** 1正常 2停用 3已归档 */
    private Integer status;

    private String remark;
}
