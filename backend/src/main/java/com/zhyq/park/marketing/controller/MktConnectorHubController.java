package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktConnectorHubService;
import com.zhyq.park.marketing.service.MktPublicSeaWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 7大域 18 通道全量热插拔外部数据连接器管理与业务核验接口（严格隔离外部 ROLE_MP）。
 */
@Tag(name = "全民营销-外部数据连接器中心与防刷验票")
@RestController
@RequestMapping("/crm/marketing/connectors")
@RequiredArgsConstructor
public class MktConnectorHubController {

    private final MktConnectorHubService connectorHub;
    private final MktPublicSeaWashService washService;

    @Operation(summary = "读取全部18条连接器脱敏配置与三层下钻同源汇总")
    @PreAuthorize("hasAnyAuthority('crm:marketing:setting:query', 'crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(connectorHub.hubOverview());
    }

    @Operation(summary = "保存单个连接器开关、广东省钱锁、月度限额与密钥凭证")
    @PreAuthorize("hasAnyAuthority('crm:marketing:setting:config', 'crm:marketing:public-sea:edit')")
    @PutMapping("/{connectorCode}")
    public Result<Map<String, Object>> save(@PathVariable String connectorCode, @RequestBody Map<String, Object> body) {
        return Result.ok(connectorHub.saveConnector(connectorCode, body));
    }

    @Operation(summary = "执行单个连接器连通性与密钥健康自检")
    @PreAuthorize("hasAnyAuthority('crm:marketing:setting:query', 'crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @PostMapping("/{connectorCode}/ping")
    public Result<Map<String, Object>> ping(@PathVariable String connectorCode) {
        return Result.ok(connectorHub.pingConnector(connectorCode));
    }

    @Operation(summary = "蝉妈妈/店透巢/探迹 在线拉取广东痛点店铺榜或招打包工企业入池")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/pull-leads")
    public Result<Map<String, Object>> pullLeads(@RequestBody Map<String, Object> body) {
        String code = body.get("connectorCode") == null ? "CHANMAMA_API" : String.valueOf(body.get("connectorCode"));
        String zoneCode = body.get("zoneCode") == null ? "GZ" : String.valueOf(body.get("zoneCode"));
        String category = body.get("category") == null ? "个人洗护" : String.valueOf(body.get("category"));
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        return Result.ok(connectorHub.pullOnlineLeads(code, zoneCode, category, washService, projectId));
    }

    @Operation(summary = "生成抖店/淘宝1688/拼多多 10秒官方扫码验店邀请链接")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/customers/{customerId}/shop-verify-invite")
    public Result<Map<String, Object>> createShopVerifyInvite(@PathVariable Long customerId, @RequestBody Map<String, Object> body) {
        String platform = body.get("platform") == null ? "DOUYIN" : String.valueOf(body.get("platform"));
        String shopName = body.get("shopName") == null ? null : String.valueOf(body.get("shopName"));
        return Result.ok(connectorHub.createShopVerifyInvite(customerId, platform, shopName));
    }

    @Operation(summary = "查询客户名下扫码验店真实单量核验记录")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:query', 'crm:marketing:customer:query')")
    @GetMapping("/customers/{customerId}/shop-verifications")
    public Result<List<Map<String, Object>>> listShopVerifications(@PathVariable Long customerId) {
        return Result.ok(connectorHub.listShopVerifications(customerId));
    }

    @Operation(summary = "阿里云 AXB 隐私小号一键外呼绑定")
    @PreAuthorize("hasAnyAuthority('crm:marketing:public-sea:edit', 'crm:marketing:customer:edit')")
    @PostMapping("/customers/{customerId}/privacy-call")
    public Result<Map<String, Object>> privacyCall(@PathVariable Long customerId, @RequestBody(required = false) Map<String, Object> body) {
        String salesPhone = body == null || body.get("salesPhone") == null ? null : String.valueOf(body.get("salesPhone"));
        return Result.ok(connectorHub.bindPrivacyCall(customerId, salesPhone));
    }

    @Operation(summary = "执行快递100真实物流轨迹 2% 随机防刷抽检")
    @PreAuthorize("hasAnyAuthority('crm:marketing:settlement:edit', 'crm:marketing:setting:config', 'crm:marketing:public-sea:edit')")
    @PostMapping("/waybill-spot-check")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> runWaybillSpotCheck(@RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") instanceof Number n ? n.longValue() : null;
        String settleMonth = body.get("settleMonth") == null ? null : String.valueOf(body.get("settleMonth"));
        List<Map<String, String>> waybills = (List<Map<String, String>>) body.get("waybills");
        return Result.ok(connectorHub.runWaybillSpotCheck(warehouseId, settleMonth, waybills));
    }

    @Operation(summary = "查询快递100运单轨迹防刷抽检历史台账")
    @PreAuthorize("hasAnyAuthority('crm:marketing:settlement:query', 'crm:marketing:settlement:edit', 'crm:marketing:setting:query', 'crm:marketing:customer:query')")
    @GetMapping("/waybill-spot-checks")
    public Result<List<Map<String, Object>>> listWaybillChecks(@RequestParam(required = false) String settleMonth) {
        return Result.ok(connectorHub.listWaybillChecks(settleMonth));
    }

    @Operation(summary = "P4事业部企业提现 · 6%增值税专用发票OCR与税务总局真伪核验")
    @PreAuthorize("hasAnyAuthority('crm:marketing:withdrawal:query', 'crm:marketing:withdrawal:audit', 'crm:marketing:setting:config', 'crm:marketing:customer:edit')")
    @PostMapping("/invoice-verify")
    public Result<Map<String, Object>> verifyInvoice(@RequestBody Map<String, Object> body) {
        return Result.ok(connectorHub.verifyVatInvoice(body));
    }

    @Operation(summary = "查询增值税专票核验审计记录")
    @PreAuthorize("hasAnyAuthority('crm:marketing:withdrawal:query', 'crm:marketing:setting:query', 'crm:marketing:customer:query')")
    @GetMapping("/invoice-verify-logs")
    public Result<List<Map<String, Object>>> listInvoiceLogs(@RequestParam(required = false) Long withdrawalId) {
        return Result.ok(connectorHub.listInvoiceLogs(withdrawalId));
    }
}
