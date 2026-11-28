package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 巡检模板检查项(pm_routine_template_item) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_routine_template_item")
public class PmRoutineTemplateItem extends BaseEntity {
    private Long templateId;
    private Integer seqNo;
    private String itemName;
    private String areaPoint;
    private String standard;
    private Integer requirePhoto;
    private Integer minPhotos;
}
