package com.zhyq.park.building.controller;

import com.zhyq.park.building.service.FloorPlanService;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/building/floor/{floorId}/plan")
@RequiredArgsConstructor
public class FloorPlanController {
    private final FloorPlanService plans;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_admin', 'building:floorPlan:query', 'building:floorPlan:edit', 'property:workorder:query', 'property:workorder:edit')")
    public Result<SysFile> current(@PathVariable Long floorId) {
        return Result.ok(plans.current(floorId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_admin', 'building:floorPlan:edit')")
    public Result<SysFile> upload(@PathVariable Long floorId, @RequestParam("file") MultipartFile file) {
        return Result.ok(plans.upload(floorId, file));
    }
}
