package com.zhyq.park.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 资料档案。状态:1现行 2失效;密级:1公开 2内部 3机密 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arc_doc")
public class ArcDoc extends BaseEntity {
    private Long projectId;
    private String archiveNo;
    private String name;
    private String originalName;
    private String categoryCode;
    private String docType;
    private Integer secretLevel;
    private LocalDate docDate;
    private String period;
    private String versionLabel;
    private String counterparty;
    private LocalDate expireDate;
    private Integer status;
    private String summary;
    private Long fileId;
    private String fileExt;
    private Long fileSize;
    private String fileHash;
    private String sourcePath;
    private String remark;
}
