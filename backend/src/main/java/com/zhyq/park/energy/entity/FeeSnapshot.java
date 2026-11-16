package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 确认出账时冻结的工作表(电表 / 水表 / 垃圾),历史月份按它展示,不随表计后续增减改变。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_fee_snapshot")
public class FeeSnapshot extends BaseEntity {
    private Long feePeriodId;
    /** ELECTRIC / WATER / GARBAGE */
    private String kind;
    private String content;
}
