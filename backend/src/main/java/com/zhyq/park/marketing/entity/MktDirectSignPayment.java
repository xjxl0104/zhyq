package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper = true) @TableName("crm_direct_sign_payment")
public class MktDirectSignPayment extends BaseEntity {
    private Long contractId; private Long warehouseId; private String paymentNo; private String payProof; private BigDecimal amount; private Integer status; private LocalDateTime paidAt; private Long projectId;
}
