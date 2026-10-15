package com.zhyq.park.marketing.open;

import com.zhyq.park.marketing.entity.MktWarehouseErp;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/open/v1/erp")
@RequiredArgsConstructor
public class ErpHeartbeatController {
    private final ErpSignatureService signatureService;

    @Operation(summary = "签名心跳：body 为空串，使用标准 HMAC 头；建议每小时调用")
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestHeader(value = "X-App-Id", required = false) String appId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        ErpSignatureService.VerifyResult result = signatureService.verify(appId, timestamp, nonce, signature, "");
        if (result instanceof ErpSignatureService.Fail failure)
            return ResponseEntity.badRequest().body(Map.of("ok", false, "code", failure.code(), "msg", failure.message()));
        MktWarehouseErp credential = ((ErpSignatureService.Ok) result).credential();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true); response.put("server_time", System.currentTimeMillis() / 1000);
        response.put("env", credential.getEnv());
        response.put("erp_status", ErpConnectionService.statusOf(credential, LocalDateTime.now()));
        response.put("last_heartbeat_at", credential.getLastHeartbeatAt());
        response.put("last_sync_at", credential.getLastSyncAt());
        return ResponseEntity.ok(response);
    }
}
