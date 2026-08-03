package com.zhyq.park.receivable.dto;

public record ReceivableBindRequest(
        Long rowId,
        Long tenantRefId,
        Long spaceId,
        Long roomId,
        Long contractId
) {}
