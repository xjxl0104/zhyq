package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 收据打印日志(fin_receipt_log)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receipt_log")
public class ReceiptLog extends BaseEntity {

    private Long receiptId;
    /** 操作人 */
    private String operator;
    /** 打印时间 */
    private LocalDateTime printTime;
}
