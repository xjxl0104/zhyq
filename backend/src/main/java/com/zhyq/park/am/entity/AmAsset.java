package com.zhyq.park.am.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产台账(am_asset)。
 * 与物业设施台账 pm_asset 相互独立:本表面向可移动资产的签出/签入状态机 + 盘点,挂 #3 空间树。
 * 状态:1在库 2领用中 3维修 4报废。
 * price 仅记录,不入账不折旧(财务边界)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("am_asset")
public class AmAsset extends BaseEntity {

    /** 资产编号(唯一) */
    private String assetNo;
    /** 资产名称 */
    private String name;
    /** 分类:IT/OFFICE/FURNITURE/DEVICE/VEHICLE/OTHER */
    private String category;
    /** 所在空间(挂 #3 sys_space 树) */
    private Long spaceId;
    /** 状态:1在库 2领用中 3维修 4报废 */
    private Integer status;
    /** 当前持有人(占位) */
    private String holder;
    /** 购置日期 */
    private LocalDate purchaseDate;
    /** 购置价(仅记录,不入账不折旧) */
    private BigDecimal price;
    /** 备注 */
    private String remark;
}
