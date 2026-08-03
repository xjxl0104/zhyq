package com.zhyq.park.receivable.dto;

import java.util.List;

/**
 * 建档执行请求:用户在预览里可改名/改类型/选复用后回传。
 */
public record ReceivableProvisionRequest(
        List<TenantDecision> tenants,
        List<SpaceDecision> spaces
) {
    public record TenantDecision(String rawName, String finalName,
                                 Integer tenantType, Long reuseTenantId) {}

    public record SpaceDecision(String rawFloor, Long reuseRoomId) {}
}
