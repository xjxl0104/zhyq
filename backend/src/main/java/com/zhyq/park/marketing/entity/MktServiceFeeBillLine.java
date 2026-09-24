package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_service_fee_bill_line")
public class MktServiceFeeBillLine extends BaseEntity {
    private Long billId;
    private Long referralOrderId;
    private Integer sourceType;
    private String sourceNo;
    private BigDecimal amount;
    private String snapshotJson;
}
