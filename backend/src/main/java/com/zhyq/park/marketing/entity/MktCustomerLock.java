package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 伙伴锁客(报备两段式)(表 crm_customer_lock,V57; active_key 修复见 V60) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_lock")
public class MktCustomerLock extends BaseEntity {
    private Long customerId;
    private Long promoterId;
    /** 1预锁 2有效锁定 3已成交 4已释放 */
    private Integer status;
    /** 生成列:deleted=0 且 status IN (1,2) 时 = customer_id,唯一键保证一个客户只有一把活锁;只读 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeKey;
    private LocalDateTime prelockUntil;
    private LocalDateTime lockUntil;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private Integer extendedCount;
    private String extendReason;
    private String releasedReason;
    private String releasedBy;
    private LocalDateTime releasedAt;
    private Long projectId;
}
