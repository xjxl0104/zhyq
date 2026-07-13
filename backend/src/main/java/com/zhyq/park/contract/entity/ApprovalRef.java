package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批实例引用(biz_approval)——仅用于合同提交审批时写入审批单,
 * 避免 contract 包反向依赖 oa 包(同 FinBill/RoomRef 的跨表引用手法)。
 * 状态:1草稿 2审批中 3已通过 4已驳回 5已撤回 6已终止
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_approval")
public class ApprovalRef extends BaseEntity {

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
}
