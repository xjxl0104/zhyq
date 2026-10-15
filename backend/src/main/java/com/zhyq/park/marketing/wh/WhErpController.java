package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.zhyq.park.marketing.open.ErpConnectionService;
import com.zhyq.park.marketing.open.ErpEventContract;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
    private final ErpConnectionService connectionService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<Map<String, Object>> current() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, w.getId()));
        connectionService.refresh(e, LocalDateTime.now());
        return Result.ok(view(e, null));
    }

    @Operation(summary = "签发沙箱凭证")
    @PostMapping("/issue-sandbox")
    @Transactional
    public Result<Map<String, Object>> issueSandbox() {
        MktWarehouse w = warehouseMapper.selectForUpdate(WhAuthContext.currentWarehouseId());
        if (w == null) throw new BizException("云仓不存在或无权访问");
        MktWarehouseErp current = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, w.getId()));
        if (current != null) return Result.ok(view(current, null));
        String secret = ErpSignatureService.newSecret();
        MktWarehouseErp e = new MktWarehouseErp(); e.setWarehouseId(w.getId()); e.setMode(1); e.setAppId(ErpSignatureService.newAppId());
        e.setSecretEnc(encryption.encrypt(secret)); e.setEnv(1); e.setStatus(1); e.setProjectId(w.getProjectId());
        erpMapper.insert(e);
        connectionService.invalidate(e);
        return Result.ok(view(e, secret));
    }

    @PostMapping("/test-orders")
    public Result<Map<String, Object>> testOrders(@RequestBody(required = false) List<Map<String, Object>> events) {
        WhAuthContext.requireWarehouse(warehouseMapper);
        if (events == null || events.isEmpty() || events.size() > 500)
            throw new BizException("请提交 1 至 500 条待校验事件");
        int accepted = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("index", index + 1);
            try {
                ErpEventContract.Event event = ErpEventContract.parseAndValidate(objectMapper.valueToTree(events.get(index)));
                row.put("ok", true); row.put("eventId", event.eventId()); row.put("orderNo", event.orderNo());
                accepted++;
            } catch (BizException invalid) {
                row.put("ok", false); row.put("code", "BAD_PAYLOAD"); row.put("msg", invalid.getMessage());
            }
            results.add(row);
        }
        // No ingress, inbox, customer mapping, contract, or commission writes are performed by format validation.
        return Result.ok(Map.of("sandbox", true, "validationOnly", true, "accepted", accepted,
                "rejected", events.size() - accepted, "results", results, "formalCommission", false,
                "businessValidated", false, "message", "仅校验事件格式；不代表签名、客户映射、合同或计佣联调通过"));
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
        connectionService.refresh(e, LocalDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>(); out.put("ok", true); out.put("serverTime", System.currentTimeMillis() / 1000);
        out.put("erpStatus", ErpConnectionService.statusOf(e, LocalDateTime.now()));
        out.put("lastHeartbeatAt", e == null ? null : e.getLastHeartbeatAt());
        out.put("lastSyncAt", e == null ? null : e.getLastSyncAt());
        out.put("connectionVerified", false);
        out.put("message", "园区服务可访问；此检查不会验证 ERP 签名或更新 ERP 心跳");
        return Result.ok(out);
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
        out.put("lastHeartbeatAt", e.getLastHeartbeatAt());
        out.put("erpStatus", ErpConnectionService.statusOf(e, LocalDateTime.now()));
        out.put("heartbeatEndpoint", "/api/open/v1/erp/heartbeat");
        out.put("endpoint", "/api/open/v1/erp/order-event"); return out;
    }
}
