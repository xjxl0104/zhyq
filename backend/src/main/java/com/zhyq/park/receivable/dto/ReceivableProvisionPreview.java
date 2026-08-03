package com.zhyq.park.receivable.dto;

import java.util.List;

/**
 * 建档预览:列出该批次"表格有、系统没有(或可复用)"的租户与空间待建清单。
 */
public record ReceivableProvisionPreview(
        Long batchId,
        List<TenantItem> tenants,
        List<SpaceItem> spaces
) {
    /** existingTenantId 非空 = 系统已有同名租户,可复用不新建。 */
    public record TenantItem(String rawName, String cleanName, Integer tenantType,
                             Long existingTenantId) {}

    /** existingRoomId 非空 = 系统已有同 roomNo 房间,可复用不新建。 */
    public record SpaceItem(String rawFloor, String projectName, String buildingName,
                            String floorName, String roomNo, Long existingRoomId) {}
}
