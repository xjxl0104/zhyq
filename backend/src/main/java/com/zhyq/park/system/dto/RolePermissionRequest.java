package com.zhyq.park.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionRequest {
    private List<Long> menuIds;
}
