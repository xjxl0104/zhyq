package com.zhyq.park.receivable.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReceivableWorkbookData(
        String title,
        String sheetName,
        int headerRow,
        List<RowData> rows,
        Totals totals
) {
    public record RowData(
            int sourceRow,
            Integer seqNo,
            String agreementNoRaw,
            String tenantNameRaw,
            String spaceNameRaw,
            BigDecimal chargeArea,
            BigDecimal actualArea,
            BigDecimal sharedArea,
            String contractTermRaw,
            BigDecimal contractRentTotal,
            String contractPeriodRaw,
            String escalationRaw,
            String freeTermRaw,
            String freePeriodRaw,
            String discountRaw,
            String rentRateRaw,
            String propertyRateRaw,
            BigDecimal monthlyRent,
            BigDecimal monthlyProperty,
            BigDecimal monthlyTotal,
            BigDecimal rentDeposit,
            BigDecimal propertyDeposit,
            String collectionTimingRaw,
            String firstCollectionRaw,
            String rentAccountRaw,
            String propertyAccountRaw,
            String notesRaw,
            BigDecimal depositDifference,
            Map<String, String> rawValues,
            Map<String, String> formulas
    ) {}

    public record Totals(
            BigDecimal chargeArea,
            BigDecimal actualArea,
            BigDecimal sharedArea,
            BigDecimal contractRentTotal,
            BigDecimal monthlyRent,
            BigDecimal monthlyProperty,
            BigDecimal monthlyTotal,
            BigDecimal rentDeposit,
            BigDecimal propertyDeposit
    ) {}
}
