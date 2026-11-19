package com.zhyq.park.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资料文件夹(多级,像电脑文件夹)。path 为物化 id 路径,如 /1/5/12/ */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arc_folder")
public class ArcFolder extends BaseEntity {
    private Long parentId;
    private String name;
    private String path;
    private Integer level;
    private Integer sort;
}
