package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_seal")
public class OaSeal extends BaseEntity {
    private String name;
    private String category;
    private String orgName;
    /** 透明底 PNG Data URI */
    private String imageData;
    private Integer defaultWidthMm;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
