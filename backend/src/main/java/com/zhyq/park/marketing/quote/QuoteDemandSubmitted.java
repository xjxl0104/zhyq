package com.zhyq.park.marketing.quote;

/** Published inside the intake transaction; external work must subscribe after commit. */
public record QuoteDemandSubmitted(long customerId, long tenantId, Long projectId) {}
