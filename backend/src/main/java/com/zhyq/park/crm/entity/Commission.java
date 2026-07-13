package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_commission")
public class Commission extends BaseEntity {
    /** 渠道 */
    private Long channelId;
    /** 关联合同 */
    private Long contractId;
    /** 计佣基数(首年租金) */
    private BigDecimal baseAmount;
    /** 佣金比例% */
    private BigDecimal rate;
    /** 佣金额 */
    private BigDecimal commission;
    /** 状态:1待结算 2已结算 3已作废 */
    private Integer status;
    private LocalDateTime settleTime;
    private String remark;
}
