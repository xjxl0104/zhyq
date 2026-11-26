package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_lease_fee_notice_item")
public class LeaseFeeNoticeItem extends BaseEntity {

    public static final String T_RENT = "RENT";
    public static final String T_PROPERTY = "PROPERTY";
    public static final String T_CUSTOM = "CUSTOM";

    private Long noticeId;
    private Long leasePeriodId;
    private Long contractId;
    private String seq;
    private String itemType;
    private Integer manualFlag;
    private String feeItem;
    private String unitName;
    private BigDecimal leaseArea;
    private BigDecimal unitPrice;
    private BigDecimal standardAmount;
    private BigDecimal discountFactor;
    private BigDecimal amount;
    private String remark;
    private Integer sort;

    public boolean isManualItem() {
        return Integer.valueOf(1).equals(manualFlag) || T_CUSTOM.equals(itemType);
    }
}
