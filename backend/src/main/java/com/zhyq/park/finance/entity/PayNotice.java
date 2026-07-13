package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款通知(fin_pay_notice)。
 * 状态:1待发送 2已发送。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_pay_notice")
public class PayNotice extends BaseEntity {

    /** 通知单号 */
    private String noticeNo;
    private Long billId;
    private Long tenantRefId;
    /** 通知金额(欠款) */
    private BigDecimal amount;
    /** 站内信/短信/微信 */
    private String sendChannel;
    private LocalDateTime sendTime;
    /** 1待发送 2已发送 */
    private Integer status;
}
