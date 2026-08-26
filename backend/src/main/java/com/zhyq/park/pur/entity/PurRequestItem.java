package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购申请明细(pur_request_item)。amount = qty * unitPrice,由服务层计算写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_request_item")
public class PurRequestItem extends BaseEntity {

    private Long requestId;
    private String itemName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
