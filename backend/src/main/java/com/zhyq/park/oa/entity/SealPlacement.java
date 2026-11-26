package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_seal_placement")
public class SealPlacement extends BaseEntity {
    private String templateKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sealId;
    private Integer enabled;
    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal scale;
    private BigDecimal rotation;
    private BigDecimal opacity;

    @TableField(exist = false)
    private String sealName;
    @TableField(exist = false)
    private String sealImageData;
    @TableField(exist = false)
    private Integer defaultWidthMm;
}
