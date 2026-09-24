package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 钻石合伙人团队份额覆盖(表 crm_position_override,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_position_override")
public class MktPositionOverride extends BaseEntity {
    /** 钻石合伙人(顶格岗位)本人 */
    private Long ownerPromoterId;
    private String positionCode;
    /** 下调后的份额,share_min_pct ≤ 值 ≤ 默认 */
    private Integer sharePct;
    private Long projectId;
}
