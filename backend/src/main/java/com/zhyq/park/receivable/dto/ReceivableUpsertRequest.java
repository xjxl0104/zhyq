package com.zhyq.park.receivable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 手工登记编辑器可改的字段。
 *
 * 原来只有租户/空间/三个月度金额,新增出来的行没有面积、没有合同起止、没有免租期,
 * 既推不出账单也看不出是谁的单 —— 等于建了一条死数据。这里补齐「一条登记能自己
 * 出账」所需的最小字段集:面积 + 合同起止 + 单价文本 + 免租/优惠 + 保证金 + 收款约定。
 */
public record ReceivableUpsertRequest(
        Long id,
        String agreementNoRaw,
        String tenantNameRaw,
        String spaceNameRaw,
        BigDecimal chargeArea,
        BigDecimal actualArea,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        String rentRateRaw,
        String propertyRateRaw,
        String freePeriodRaw,
        String freeTermRaw,
        String discountRaw,
        BigDecimal monthlyRent,
        BigDecimal monthlyProperty,
        BigDecimal monthlyTotal,
        BigDecimal rentDeposit,
        BigDecimal propertyDeposit,
        String collectionTimingRaw,
        String notesRaw,
        Long tenantRefId,
        Long spaceId,
        Long roomId,
        Long contractId
) {}
