package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_competition_protection")
public class MktCompetitionProtection extends BaseEntity {
    private Long warehouseId;
    private String nameEnc;
    private String nameFingerprint;
}
