package com.zhyq.park.vending.dto;

import java.util.List;
import java.util.Map;

public record VendingImportPreview(
        Long batchId,
        String fileName,
        String type,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        int excludedRows,
        List<RowPreview> rows
) {
    public record RowPreview(
            Long rowId,
            int rowNo,
            String status,
            String errorMessage,
            Map<String, String> values
    ) {}
}
