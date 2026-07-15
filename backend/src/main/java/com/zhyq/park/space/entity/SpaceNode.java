package com.zhyq.park.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_space")
public class SpaceNode extends BaseEntity {
    private Long parentId;
    private String path;
    private Integer level;
    private String type;      // PROJECT/BUILDING/FLOOR/ROOM
    private String code;
    private String name;
    private String refType;   // project/building/floor/room
    private Long refId;
    private Integer sort;
    private Integer status;
}
