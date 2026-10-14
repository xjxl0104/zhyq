package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktCompetitionProtectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The confidential cross-warehouse protection list is visible to park administrators only. */
@Tag(name = "全民营销-竞争保护白名单")
@RestController
@RequestMapping("/crm/marketing/competition-protection")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class MktCompetitionProtectionAdminController {
    private final MktCompetitionProtectionService protectionService;

    @Operation(summary = "分页查看当前园区内加盟云仓的竞争保护白名单")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100)
            throw new BizException(400, "分页参数无效");
        return Result.ok(protectionService.adminPage(projectId, warehouseId, pageNo, pageSize));
    }
}
