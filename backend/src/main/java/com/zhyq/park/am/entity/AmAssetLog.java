package com.zhyq.park.am.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资产签出签入/盘点流水(am_asset_log)。
 * action:CHECKOUT 签出 / CHECKIN 签入 / INVENTORY 盘点 / SCRAP 报废 / REPAIR 维修(含维修完成)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("am_asset_log")
public class AmAssetLog extends BaseEntity {

    /** 资产ID */
    private Long assetId;
    /** 动作:CHECKOUT/CHECKIN/INVENTORY/SCRAP/REPAIR */
    private String action;
    /** 操作人(占位) */
    private String operator;
    /** 签出目标人 */
    private String holder;
    /** 盘点/移动到的空间 */
    private Long spaceId;
    /** 备注 */
    private String remark;
    /** 操作时间 */
    private LocalDateTime actTime;
}
