package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 责任单位主数据:工单(尤其报修)的承接方。
 * 与 sys_dept(内部组织架构)、iot_vendor(IoT 对接凭证)语义不同,故单独建表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_responsible_unit")
public class ResponsibleUnit extends BaseEntity {

    /** 单位名称,导入时按此字段去重 upsert */
    private String name;

    /** 内部部门/外部供应商/物业/施工方 */
    private String unitType;

    private String contact;

    private String contactPhone;

    /** 服务范围/专业,如 电梯/消防/空调 */
    private String serviceScope;

    /** 所属园区,NULL 表示全局通用 */
    private Long projectId;

    private Integer enabled;

    private String remark;
}
