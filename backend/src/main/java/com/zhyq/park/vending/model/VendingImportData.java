package com.zhyq.park.vending.model;

import java.util.List;
import java.util.Map;

public record VendingImportData(
        VendingImportType type,
        List<String> headers,
        List<RowData> rows
) {
    public record RowData(
            int rowNo,
            Map<String, String> values,
            Map<String, String> formulas,
            List<String> errors
    ) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }
}
