package com.zhyq.park.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 自动归类关键词规则 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arc_rule")
public class ArcRule extends BaseEntity {
    private String keyword;
    /** 1 文件名与来源路径 2 文件正文 */
    private Integer scope;
    private String categoryCode;
    private String docType;
    private Integer secretLevel;
    private Integer weight;
    private Integer enabled;
    private String remark;
}
