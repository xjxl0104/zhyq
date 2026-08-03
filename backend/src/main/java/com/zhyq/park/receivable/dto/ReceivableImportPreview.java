package com.zhyq.park.receivable.dto;

import com.zhyq.park.receivable.model.ReceivableWorkbookData;

import java.util.List;

public record ReceivableImportPreview(
        Long batchId,
        String fileName,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        ReceivableWorkbookData.Totals totals,
        List<RowPreview> rows
) {
    public record RowPreview(
            Long rowId,
            int sourceRow,
            Integer seqNo,
            String agreementNo,
            String tenantName,
            String spaceName,
            String status,
            String errorMessage,
            String rentAccountMasked,
            String propertyAccountMasked
    ) {}
}
