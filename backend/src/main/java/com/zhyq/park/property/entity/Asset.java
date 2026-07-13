package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产(pm_asset)
 * 资产状态:1在用 2闲置 3维修 4报废
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_asset")
public class Asset extends BaseEntity {

    /** 资产编码 */
    private String code;
    /** 资产名称 */
    private String name;
    /** 分类:办公设备/机电/安防/家具/IT */
    private String category;
    private Long projectId;
    private Long buildingId;
    /** 存放位置 */
    private String location;
    /** 采购价 */
    private BigDecimal price;
    /** 采购日期 */
    private LocalDate purchaseDate;
    /** 保修到期 */
    private LocalDate warrantyEnd;
    /** 责任人 */
    private String owner;
    /** 资产状态:1在用 2闲置 3维修 4报废 */
    private Integer status;
    private String remark;
}
