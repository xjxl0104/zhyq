package com.zhyq.park.receivable.dto;

import java.math.BigDecimal;

/** Only fields that may be changed through the manual register editor. */
public record ReceivableUpsertRequest(
        Long id,
        String agreementNoRaw,
        String tenantNameRaw,
        String spaceNameRaw,
        BigDecimal monthlyRent,
        BigDecimal monthlyProperty,
        BigDecimal monthlyTotal,
        Long tenantRefId,
        Long spaceId,
        Long roomId,
        Long contractId
) {}
