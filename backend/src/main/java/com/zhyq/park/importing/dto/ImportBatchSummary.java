package com.zhyq.park.importing.dto;

import com.zhyq.park.importing.entity.ImportBatch;

import java.time.LocalDateTime;

public record ImportBatchSummary(
        Long id,
        String bizType,
        String fileName,
        String status,
        Integer totalRows,
        Integer importedRows,
        String confirmedBy,
        LocalDateTime confirmedTime,
        String rollbackBy,
        LocalDateTime rollbackTime
) {
    public static ImportBatchSummary from(ImportBatch batch) {
        return new ImportBatchSummary(batch.getId(), batch.getBizType(), batch.getFileName(),
                batch.getStatus(), batch.getTotalRows(), batch.getImportedRows(),
                batch.getConfirmedBy(), batch.getConfirmedTime(), batch.getRollbackBy(),
                batch.getRollbackTime());
    }
}
