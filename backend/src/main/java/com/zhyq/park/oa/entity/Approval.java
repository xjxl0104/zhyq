package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批实例(biz_approval)
 * 业务类型:contract/refund/adjust/terminate
 * 状态:1草稿 2审批中 3已通过 4已驳回 5已撤回 6已终止(附录B)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_approval")
public class Approval extends BaseEntity {

    /** 业务类型:contract/refund/adjust/terminate */
    private String bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 审批标题 */
    private String title;
    /** 审批状态 */
    private Integer status;
    /** 申请人 */
    private String applyBy;
    /** 审批人 */
    private String approveBy;
    /** 审批时间 */
    private LocalDateTime approveTime;
    /** 审批意见 */
    private String opinion;
}
