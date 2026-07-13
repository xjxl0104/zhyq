package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 安防巡更记录(pm_patrol)
 * 结果:正常/异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_patrol")
public class Patrol extends BaseEntity {

    /** 巡更路线 */
    private String routeName;
    /** 点位 */
    private String point;
    /** 巡更人 */
    private String patroller;
    private LocalDateTime patrolTime;
    /** 正常/异常 */
    private String result;
    private String remark;
}
