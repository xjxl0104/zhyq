package com.zhyq.park.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资料分类(门类 + 子类),门类 parentCode 为空 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arc_category")
public class ArcCategory extends BaseEntity {
    private String parentCode;
    private String code;
    private String name;
    private Integer sort;
    /** 默认密级 1公开 2内部 3机密 */
    private Integer defaultSecret;
    private Integer enabled;
    private String remark;
}
