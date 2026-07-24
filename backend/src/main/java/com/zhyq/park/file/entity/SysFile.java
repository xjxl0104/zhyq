package com.zhyq.park.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {
    private String bizType;
    private Long bizId;
    private String originalName;
    private String storePath;
    private String url;
    private Long fileSize;
    private String contentType;
    private String ext;
}
