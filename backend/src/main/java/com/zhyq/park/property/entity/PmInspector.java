package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物业巡检人员库(pm_inspector) — 四个巡检/检查模块共用 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_inspector")
public class PmInspector extends BaseEntity {
    private String name;
    private String phone;
    private String position;
    private String moduleScope;
    private Integer status;
    private String remark;
}
