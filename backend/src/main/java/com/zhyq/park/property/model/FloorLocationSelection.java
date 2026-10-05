package com.zhyq.park.property.model;

import java.math.BigDecimal;

/** Present only when a user explicitly sets or clears the work-order floor location. */
public record FloorLocationSelection(Long buildingId, Long floorId, Long planFileId,
                                     BigDecimal x, BigDecimal y) {}
