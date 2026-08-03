package com.zhyq.park.importing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_import_batch")
public class ImportBatch extends BaseEntity {
    private String bizType;
    private String sourceSystem;
    private String fileName;
    private String fileHash;
    private Long fileId;
    private String status;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer importedRows;
    private String errorSummary;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String rollbackBy;
    private LocalDateTime rollbackTime;
}
