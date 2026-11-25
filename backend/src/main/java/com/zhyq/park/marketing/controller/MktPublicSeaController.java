package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktPublicSeaService;
import com.zhyq.park.marketing.service.MktPublicSeaWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 园区内部专属公海池管理后台接口（严格隔离外部 ROLE_MP 推广伙伴，仅限 admin / mkt_sales / mkt_ops）。
 */
@Tag(name = "全民营销-内部专属公海池")
@RestController
@RequestMapping("/crm/marketing/public-sea")
@RequiredArgsConstructor
public class MktPublicSeaController {

    private final MktPublicSeaService publicSeaService;
    private final MktPublicSeaWashService washService;

    @Operation(summary = "同源汇总与三层下钻指标")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long projectId) {
        return Result.ok(publicSeaService.summary(projectId));
    }

    @Operation(summary = "内部公海分页列表")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String seaSource,
            @RequestParam(required = false) String claimStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(publicSeaService.adminPage(projectId, seaSource, claimStatus, keyword, pageNo, pageSize));
    }

    @Operation(summary = "上传 Excel/CSV 三道防撞洗数导入(支持 dryRun 预览)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/wash-file")
    public Result<Map<String, Object>> washFile(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "CHANMAMA") String seaSource,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(washService.washFile(projectId, seaSource, file, dryRun));
    }

    @Operation(summary = "提交 JSON 名单三道防撞洗数导入(支持 dryRun 预览)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/wash-rows")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> washRows(@RequestBody Map<String, Object> body) {
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        String seaSource = body.get("seaSource") == null ? "CHANMAMA" : String.valueOf(body.get("seaSource"));
        String fileName = body.get("fileName") == null ? null : String.valueOf(body.get("fileName"));
        boolean dryRun = Boolean.TRUE.equals(body.get("dryRun"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        return Result.ok(washService.washJsonRows(projectId, seaSource, fileName, rows, dryRun));
    }

    @Operation(summary = "园区内部招商专员认领公海客户")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/claim")
    public Result<Map<String, Object>> claim(@PathVariable Long customerId) {
        return Result.ok(publicSeaService.claimByAdmin(customerId));
    }

    @Operation(summary = "登记首通核实纪要(解除72h超时弹回)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/first-touch")
    public Result<Void> firstTouch(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        Integer estDaily = body.get("estDailyOrders") instanceof Number n ? n.intValue() : null;
        String cat = body.get("category") == null ? null : String.valueOf(body.get("category"));
        publicSeaService.recordFirstTouch(customerId, note, estDaily, cat);
        return Result.ok();
    }

    @Operation(summary = "定向投喂至加盟云仓限时认领池")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/feed-warehouses")
    @SuppressWarnings("unchecked")
    public Result<Void> feedWarehouses(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        List<Number> rawIds = (List<Number>) body.get("warehouseIds");
        List<Long> warehouseIds = rawIds == null ? List.of() : rawIds.stream().map(Number::longValue).toList();
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        publicSeaService.feedToWarehouses(customerId, warehouseIds, note);
        return Result.ok();
    }

    @Operation(summary = "释放回公海或归档冷宫")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/release")
    public Result<Void> release(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        boolean archiveCold = Boolean.TRUE.equals(body.get("archiveCold"));
        publicSeaService.releaseToSea(customerId, reason, archiveCold);
        return Result.ok();
    }
}
