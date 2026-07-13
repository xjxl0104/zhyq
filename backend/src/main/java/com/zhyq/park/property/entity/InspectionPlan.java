package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检计划(pm_inspection_plan)
 * 状态:1启用 0停用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_inspection_plan")
public class InspectionPlan extends BaseEntity {

    /** 计划名称 */
    private String name;
    private Long projectId;
    /** 周期:每日/每周/每月 */
    private String cycle;
    /** 路线 */
    private String route;
    /** 点位 */
    private String points;
    /** 状态 */
    private Integer status;
}
