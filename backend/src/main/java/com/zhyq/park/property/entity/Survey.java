package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投票问卷(pm_survey)
 * 类型(stype):投票/问卷
 * 状态:1进行中 2已结束
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_survey")
public class Survey extends BaseEntity {

    private String title;
    /** 投票/问卷 */
    private String stype;
    /** 选项JSON,如 [{"label":"满意","votes":12}] */
    private String options;
    private LocalDateTime deadline;
    /** 1进行中 2已结束 */
    private Integer status;
    /** 参与人数 */
    private Integer votes;
}
