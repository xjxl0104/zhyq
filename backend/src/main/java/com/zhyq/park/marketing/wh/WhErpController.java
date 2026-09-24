package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktErpSyncLog;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.open.ErpSignatureService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "云仓端-ERP 自助")
@RestController
@RequestMapping("/wh/v1/erp")
@RequiredArgsConstructor
public class WhErpController {
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseErpMapper erpMapper;
    private final MktErpSyncLogMapper logMapper;
    private final FieldEncryptionService encryption;

    @GetMapping
    public Result<Map<String, Object>> current() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, w.getId()));
        return Result.ok(view(e, null));
    }

    @Operation(summary = "签发沙箱凭证")
    @PostMapping("/issue-sandbox")
    public Result<Map<String, Object>> issueSandbox() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktWarehouseErp current = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, w.getId()));
        if (current != null) return Result.ok(view(current, null));
        String secret = ErpSignatureService.newSecret();
        MktWarehouseErp e = new MktWarehouseErp(); e.setWarehouseId(w.getId()); e.setMode(1); e.setAppId(ErpSignatureService.newAppId());
        e.setSecretEnc(encryption.encrypt(secret)); e.setEnv(1); e.setStatus(1); e.setProjectId(w.getProjectId());
        erpMapper.insert(e);
        return Result.ok(view(e, secret));
    }

    @PostMapping("/test-orders")
    public Result<Map<String, Object>> testOrders(@RequestBody(required = false) List<Map<String, Object>> events) {
        WhAuthContext.requireWarehouse(warehouseMapper);
        int count = events == null ? 0 : events.size();
        // Sandbox self-test is deliberately a dry run: it never calls formal ingest/commission services.
        return Result.ok(Map.of("sandbox", true, "accepted", count, "formalCommission", false));
    }

    @GetMapping("/test-results")
    public Result<PageResult<MktErpSyncLog>> testResults(@RequestParam(defaultValue = "1") int pageNo,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return logs(pageNo, pageSize, null);
    }

    @GetMapping("/sync-logs")
    public Result<PageResult<MktErpSyncLog>> syncLogs(@RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return logs(pageNo, pageSize, null);
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, w.getId()));
        Map<String, Object> out = new LinkedHashMap<>(); out.put("ok", true); out.put("serverTime", System.currentTimeMillis() / 1000);
        out.put("erpStatus", w.getErpStatus()); out.put("lastSyncAt", e == null ? null : e.getLastSyncAt()); return Result.ok(out);
    }

    private Result<PageResult<MktErpSyncLog>> logs(int pageNo, int pageSize, Integer ok) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktErpSyncLog> p = logMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktErpSyncLog>()
                .eq(MktErpSyncLog::getWarehouseId, w.getId()).eq(w.getProjectId() != null, MktErpSyncLog::getProjectId, w.getProjectId())
                .eq(ok != null, MktErpSyncLog::getOk, ok).orderByDesc(MktErpSyncLog::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    private static Map<String, Object> view(MktWarehouseErp e, String secret) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (e == null) { out.put("configured", false); return out; }
        out.put("configured", true); out.put("id", e.getId()); out.put("appId", e.getAppId()); out.put("env", e.getEnv());
        out.put("status", e.getStatus()); out.put("lastSyncAt", e.getLastSyncAt()); out.put("secret", secret);
        out.put("endpoint", "/api/open/v1/erp/order-event"); return out;
    }
}
