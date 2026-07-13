package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 房源引用(biz_room)——仅用于本模块更新房源状态,避免跨包依赖。
 * 房源状态:0未配置 1可租 2锁定 3意向占用 4签约中 5在租 6退租处理中 7维修 8停用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_room")
public class RoomRef extends BaseEntity {

    private Integer status;
}
