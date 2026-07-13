package com.zhyq.park.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    /** 父菜单ID */
    private Long parentId;
    /** 菜单名称 */
    private String name;
    /** 类型:1目录 2菜单 3按钮 */
    private Integer type;
    /** 路由路径 */
    private String path;
    /** 前端组件 */
    private String component;
    /** 权限标识 */
    private String perm;
    /** 图标 */
    private String icon;
    /** 排序 */
    private Integer sort;
    /** 是否可见:1显示 0隐藏 */
    private Integer visible;
    /** 状态:1启用 0停用 */
    private Integer status;
}
