package com.zhyq.park.building.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_room")
public class Room extends BaseEntity {
    private Long floorId;
    private Long buildingId;
    private Long projectId;
    private String code;
    private String roomNo;
    /** 状态:0未配置 1可租 2锁定 3意向占用 4签约中 5在租 6退租处理中 7维修 8停用 */
    private Integer status;
    private BigDecimal buildArea;
    private BigDecimal rentArea;
    private BigDecimal useArea;
    private String orientation;
    private String decoration;
    private String layout;
    private String usageType;
    private BigDecimal basePrice;
    private BigDecimal propertyFee;
    private String images;
    private String remark;
}
