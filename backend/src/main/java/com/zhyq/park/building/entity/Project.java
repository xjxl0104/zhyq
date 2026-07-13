package com.zhyq.park.building.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project")
public class Project extends BaseEntity {
    private String code;
    private String name;
    private String type;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal manageArea;
    private BigDecimal buildArea;
    private Integer status;
    private String manager;
    private String remark;
}
