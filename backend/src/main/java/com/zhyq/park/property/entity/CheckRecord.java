package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 三检:保洁/绿化/品质(pm_check)
 * 状态:1合格 2待整改 3已整改
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_check")
public class CheckRecord extends BaseEntity {

    /** 保洁/绿化/品质 */
    private String ctype;
    /** 检查位置 */
    private String location;
    /** 检查人 */
    private String checker;
    private LocalDateTime checkTime;
    /** 评分1-10 */
    private Integer score;
    /** 发现问题 */
    private String issues;
    /** 1合格 2待整改 3已整改 */
    private Integer status;
}
