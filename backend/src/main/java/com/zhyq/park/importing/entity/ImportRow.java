package com.zhyq.park.importing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_import_row")
public class ImportRow extends BaseEntity {
    private Long batchId;
    private String sheetName;
    private Integer rowNo;
    private String rowFingerprint;
    private String rawJson;
    private String normalizedJson;
    private String status;
    private String errorMessage;
    private String targetType;
    private Long targetId;
}
