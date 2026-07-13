package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收支流水。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_flow")
public class Flow extends BaseEntity {

    private String flowNo;
    /** 1收 2支 */
    private Integer direction;
    private BigDecimal amount;
    private Long billId;
    private Long paymentId;
    /** 0未匹配 1已匹配 */
    private Integer matchStatus;
    private LocalDateTime flowTime;
    private String remark;
}
