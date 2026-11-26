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

import java.math.BigDecimal;
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

    @Operation(summary = "同源汇总与四卡三层下钻指标")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long projectId) {
        return Result.ok(publicSeaService.summary(projectId));
    }

    @Operation(summary = "内部公海分页列表(支持广东战区/5km圈/平台/失信风险过滤)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) String seaSource,
            @RequestParam(required = false) String claimStatus,
            @RequestParam(required = false) String distFilter,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String riskFilter,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(publicSeaService.adminPage(
                projectId, zoneCode, seaSource, claimStatus, distFilter, platform, riskFilter, keyword, pageNo, pageSize));
    }

    @Operation(summary = "地图视图专用：查询当前战区已落图客户点位（上限 300 点）")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/map-points")
    public Result<List<Map<String, Object>>> mapPoints(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) String seaSource,
            @RequestParam(required = false) String claimStatus,
            @RequestParam(required = false) String distFilter) {
        return Result.ok(publicSeaService.mapPoints(projectId, zoneCode, seaSource, claimStatus, distFilter));
    }

    @Operation(summary = "查询最近洗数批次列表（页面刷新后查看后台导入历史与进度）")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/wash-batches")
    public Result<List<Map<String, Object>>> washBatches(@RequestParam(required = false) Long projectId) {
        return Result.ok(washService.listWashBatches(projectId));
    }
    @Operation(summary = "上传 Excel/CSV 榜单或名单三道防撞洗数对齐入池(支持 dryRun 预览)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/wash-file")
    public Result<Map<String, Object>> washFile(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "CHANMAMA") String seaSource,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(washService.washFile(projectId, seaSource, file, dryRun));
    }

    @Operation(summary = "提交 JSON 店铺榜单/名单三道防撞洗数对齐入池(支持 dryRun 预览)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/wash-rows")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> washRows(@RequestBody Map<String, Object> body) {
        Long projectId = asLong(body.get("projectId"));
        String seaSource = body.get("seaSource") == null ? "CHANMAMA" : String.valueOf(body.get("seaSource"));
        String fileName = body.get("fileName") == null ? null : String.valueOf(body.get("fileName"));
        boolean dryRun = Boolean.TRUE.equals(body.get("dryRun"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        return Result.ok(washService.washJsonRows(projectId, seaSource, fileName, rows, dryRun));
    }

    @Operation(summary = "查询洗数批次进度（大批量导入后台执行时轮询）")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/wash-batches/{batchNo}")
    public Result<Map<String, Object>> washBatch(@PathVariable String batchNo) {
        return Result.ok(washService.getWashBatch(batchNo));
    }

    @Operation(summary = "单店名称反查背后广东工商主体与地址并过防撞入公海")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/shop-lookup")
    public Result<Map<String, Object>> shopLookup(@RequestBody Map<String, Object> body) {
        Long projectId = asLong(body.get("projectId"));
        String shopName = body.get("shopName") == null ? null : String.valueOf(body.get("shopName"));
        String platform = body.get("platform") == null ? "抖音" : String.valueOf(body.get("platform"));
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        boolean forceUnlockRegion = Boolean.TRUE.equals(body.get("forceUnlockRegion"));
        return Result.ok(washService.lookupShopAndIngest(projectId, shopName, platform, category, forceUnlockRegion));
    }

    @Operation(summary = "高德周边 10km 广东电商园/产业带 POI 扫楼雷达入池")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/amap-radar")
    public Result<Map<String, Object>> amapRadar(@RequestBody Map<String, Object> body) {
        Long projectId = asLong(body.get("projectId"));
        BigDecimal centerLng = asDecimal(body.get("centerLng"));
        BigDecimal centerLat = asDecimal(body.get("centerLat"));
        Integer radiusMeters = body.get("radiusMeters") instanceof Number n ? n.intValue() : 10_000;
        String keywords = body.get("keywords") == null ? null : String.valueOf(body.get("keywords"));
        return Result.ok(washService.ingestAmapRadar(projectId, centerLng, centerLat, radiusMeters, keywords));
    }

    @Operation(summary = "园区内部招商专员认领公海客户")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/claim")
    public Result<Map<String, Object>> claim(@PathVariable Long customerId) {
        return Result.ok(publicSeaService.claimByAdmin(customerId));
    }

    @Operation(summary = "外勤 500m GPS 定位 + 现场门头拍照打卡(支持 WGS84 自动转 GCJ02 火星坐标，解锁 15 天保护期)")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/visit-checkin")
    public Result<Map<String, Object>> visitCheckin(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        BigDecimal lng = asDecimal(body.get("checkinLng") != null ? body.get("checkinLng") : body.get("lng"));
        BigDecimal lat = asDecimal(body.get("checkinLat") != null ? body.get("checkinLat") : body.get("lat"));
        String coordType = body.get("coordType") == null ? "GCJ02" : String.valueOf(body.get("coordType"));
        String photoUrl = body.get("photoUrl") == null ? null : String.valueOf(body.get("photoUrl"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return Result.ok(publicSeaService.visitCheckin(customerId, lng, lat, coordType, photoUrl, note));
    }

    @Operation(summary = "查询公海客户多轮跟进沟通时间线")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/{customerId}/followups")
    public Result<List<Map<String, Object>>> followups(@PathVariable Long customerId) {
        return Result.ok(publicSeaService.listFollowups(customerId));
    }

    @Operation(summary = "追加多轮跟进沟通记录（电联/微信报价/看仓）")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/{customerId}/followups")
    public Result<Map<String, Object>> addFollowup(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        String actionType = body.get("actionType") == null ? "CALL" : String.valueOf(body.get("actionType"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        java.time.LocalDateTime nextAt = null;
        if (body.get("nextFollowupAt") != null && !String.valueOf(body.get("nextFollowupAt")).isBlank()) {
            try { nextAt = java.time.LocalDateTime.parse(String.valueOf(body.get("nextFollowupAt")).trim().replace(" ", "T")); }
            catch (Exception ignored) {}
        }
        return Result.ok(publicSeaService.addFollowup(customerId, actionType, note, nextAt));
    }

    @Operation(summary = "主管/运营将公海客户定向指派给指定招商专员")
    @PreAuthorize("hasAnyRole('admin', 'mkt_ops') or hasAuthority('crm:marketing:setting:config')")
    @PostMapping("/{customerId}/assign")
    public Result<Map<String, Object>> assign(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        String target = body.get("targetUsername") == null ? null : String.valueOf(body.get("targetUsername"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return Result.ok(publicSeaService.assignToSpecialist(customerId, target, note));
    }

    @Operation(summary = "管理员将冷宫归档客户重新激活回公海池")
    @PreAuthorize("hasAnyRole('admin', 'mkt_ops') or hasAuthority('crm:marketing:setting:config')")
    @PostMapping("/{customerId}/revive")
    public Result<Void> revive(@PathVariable Long customerId, @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note"));
        publicSeaService.reviveFromCold(customerId, note);
        return Result.ok();
    }

    @Operation(summary = "查询公海客户历史现场 GPS 打卡足迹")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/{customerId}/checkins")
    public Result<List<Map<String, Object>>> checkins(@PathVariable Long customerId) {
        return Result.ok(publicSeaService.listCheckins(customerId));
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

    @Operation(summary = "批量定向投喂多户公海客户至加盟云仓限时认领池")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/batch-feed-warehouses")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> batchFeedWarehouses(@RequestBody Map<String, Object> body) {
        List<Number> rawCids = (List<Number>) body.get("customerIds");
        List<Number> rawWids = (List<Number>) body.get("warehouseIds");
        List<Long> warehouseIds = rawWids == null ? List.of() : rawWids.stream().map(Number::longValue).toList();
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        int fed = 0;
        if (rawCids != null) {
            for (Number cid : rawCids) {
                if (cid != null) {
                    publicSeaService.feedToWarehouses(cid.longValue(), warehouseIds, note);
                    fed++;
                }
            }
        }
        return Result.ok(Map.of("fedCount", fed));
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

    private static Long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null || String.valueOf(v).isBlank() ? null : Long.parseLong(String.valueOf(v).trim()); }
        catch (Exception e) { return null; }
    }

    private static BigDecimal asDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return v == null || String.valueOf(v).isBlank() ? null : new BigDecimal(String.valueOf(v).trim()); }
        catch (Exception e) { return null; }
    }
}
