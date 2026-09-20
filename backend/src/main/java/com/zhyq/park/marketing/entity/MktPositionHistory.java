package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位变更史(表 crm_position_history,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_position_history")
public class MktPositionHistory extends BaseEntity {
    private Long promoterId;
    private String fromCode;
    private String toCode;
    /** auto 自动复核 / manual 后台手动 */
    private String reason;
    private String operator;
    private String note;
    private Long projectId;
}
