package com.zhyq.park.energy.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.NoticeHeaderProfile;
import com.zhyq.park.energy.service.NoticeHeaderProfileService;
import com.zhyq.park.energy.service.NoticeHeaderProfileService.SaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "缴费管理-通知单抬头按单位定制")
@RestController
@RequestMapping("/energy/notice-header-profile")
@RequiredArgsConstructor
public class NoticeHeaderProfileController {

    private final NoticeHeaderProfileService service;

    @Operation(summary = "查询指定模块的所有定制抬头配置")
    @GetMapping
    public Result<List<NoticeHeaderProfile>> list(@RequestParam String scopeType) {
        return Result.ok(service.listByScope(scopeType));
    }

    @Operation(summary = "保存指定缴费单位的定制抬头")
    @PostMapping
    public Result<NoticeHeaderProfile> save(@RequestBody SaveRequest req) {
        return Result.ok(service.save(req));
    }

    @Operation(summary = "删除指定缴费单位的定制抬头（恢复通用）")
    @DeleteMapping
    public Result<Void> remove(@RequestParam String scopeType,
                               @RequestParam String tenantName,
                               @RequestParam(required = false) Long periodId) {
        service.remove(scopeType, tenantName, periodId);
        return Result.ok();
    }
}
