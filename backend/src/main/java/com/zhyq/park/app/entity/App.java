package com.zhyq.park.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_app")
public class App extends BaseEntity {
    /** 应用名称 */
    private String name;
    /** 分类:楼宇/租客/招商/合同/财务/内部办公/物业服务/楼宇运营/智能硬件 */
    private String category;
    /** 图标名 */
    private String icon;
    /** 跳转路由 */
    private String path;
    /** 用途说明 */
    private String description;
    /** 排序 */
    private Integer sort;
    /** 状态:1上架 0停用 */
    private Integer status;
}
