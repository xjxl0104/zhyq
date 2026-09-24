package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 云仓联系人(表 crm_warehouse_contact,V65):一个云仓可绑多个微信/手机号(P2-5)。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_warehouse_contact")
public class MktWarehouseContact extends BaseEntity {
    private Long warehouseId;
    /** 微信 openid,一个 openid 只能属于一个云仓(唯一键) */
    private String openid;
    private String unionid;
    private String phone;
    private String name;
    /** owner 主联系人 / member 普通联系人 */
    private String role;
    /** 1 正常 0 停用 */
    private Integer status;
    private Long projectId;
}
