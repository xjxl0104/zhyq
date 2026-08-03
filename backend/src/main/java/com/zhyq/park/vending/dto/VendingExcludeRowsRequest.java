package com.zhyq.park.vending.dto;

import java.util.List;

public record VendingExcludeRowsRequest(List<Long> rowIds, String reason) {}
