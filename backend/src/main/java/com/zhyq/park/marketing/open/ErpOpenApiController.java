package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云仓 ERP 开放接口(PARK-MKT-001 §5.3)。permitAll(见 SecurityConfig),自带 HMAC 验签。
 * 响应不走 Result 包装,按契约返回 {"ok":true,...} / 400 {"ok":false,"code","msg"}。
 * 重复事件返回 200(幂等);验签失败一律 400 并记 crm_erp_sync_log。
 */
@Slf4j
@Tag(name = "开放接口-云仓 ERP")
@RestController
@RequestMapping("/open/v1/erp")
@RequiredArgsConstructor
public class ErpOpenApiController {

    private static final int BATCH_MAX = 500;

    private final ErpSignatureService signatureService;
    private final ErpOrderIngestService ingestService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "订单事件(created/paid/shipped/returned/refunded/cancelled)")
    @PostMapping("/order-event")
    public ResponseEntity<Map<String, Object>> orderEvent(@RequestHeader(value = "X-App-Id", required = false) String appId,
                                                          @RequestHeader(value = "X-Timestamp", required = false) String ts,
                                                          @RequestHeader(value = "X-Nonce", required = false) String nonce,
                                                          @RequestHeader(value = "X-Signature", required = false) String sig,
                                                          @RequestBody String body) {
        String digest = ErpSignatureService.sha256Hex(body);
        ErpSignatureService.VerifyResult v = signatureService.verify(appId, ts, nonce, sig, body);
        if (v instanceof ErpSignatureService.Fail f) {
            ingestService.logRejected(appId, f.code(), f.message(), digest);
            return ResponseEntity.badRequest().body(fail(f.code(), f.message()));
        }
        MktWarehouseErp cred = ((ErpSignatureService.Ok) v).credential();
        JsonNode ev;
        try {
            ev = objectMapper.readTree(body);
        } catch (Exception e) {
            ingestService.logRejected(appId, "BAD_PAYLOAD", "JSON 解析失败", digest);
            return ResponseEntity.badRequest().body(fail("BAD_PAYLOAD", "JSON 解析失败"));
        }
        ErpOrderIngestService.IngestResult r = ingestService.ingest(cred, ev, digest);
        return r.ok() ? ResponseEntity.ok(ok(r)) : ResponseEntity.badRequest().body(fail(r.code(), r.msg()));
    }

    @Operation(summary = "批量事件(≤ 500 条,逐条返回)")
    @PostMapping("/orders/batch")
    public ResponseEntity<Map<String, Object>> batch(@RequestHeader(value = "X-App-Id", required = false) String appId,
                                                     @RequestHeader(value = "X-Timestamp", required = false) String ts,
                                                     @RequestHeader(value = "X-Nonce", required = false) String nonce,
                                                     @RequestHeader(value = "X-Signature", required = false) String sig,
                                                     @RequestBody String body) {
        String digest = ErpSignatureService.sha256Hex(body);
        ErpSignatureService.VerifyResult v = signatureService.verify(appId, ts, nonce, sig, body);
        if (v instanceof ErpSignatureService.Fail f) {
            ingestService.logRejected(appId, f.code(), f.message(), digest);
            return ResponseEntity.badRequest().body(fail(f.code(), f.message()));
        }
        MktWarehouseErp cred = ((ErpSignatureService.Ok) v).credential();
        JsonNode arr;
        try {
            arr = objectMapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(fail("BAD_PAYLOAD", "JSON 解析失败"));
        }
        if (!arr.isArray() || arr.size() > BATCH_MAX) {
            return ResponseEntity.badRequest().body(fail("BAD_PAYLOAD", "须为数组且 ≤ " + BATCH_MAX + " 条"));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode ev : arr) {
            ErpOrderIngestService.IngestResult r = ingestService.ingest(cred, ev, ErpSignatureService.sha256Hex(ev.toString()));
            Map<String, Object> m = r.ok() ? ok(r) : fail(r.code(), r.msg());
            m.put("order_no", ev.path("order_no").asText(null));
            results.add(m);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("results", results);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "心跳(有事件即视为心跳;此接口供云仓自检连通与时间同步)")
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("server_time", System.currentTimeMillis() / 1000);
        return m;
    }

    @Operation(summary = "契约:当前版本与字段(需验签,body 为空串)")
    @GetMapping("/contract")
    public ResponseEntity<Map<String, Object>> contract(@RequestHeader(value = "X-App-Id", required = false) String appId,
                                                        @RequestHeader(value = "X-Timestamp", required = false) String ts,
                                                        @RequestHeader(value = "X-Nonce", required = false) String nonce,
                                                        @RequestHeader(value = "X-Signature", required = false) String sig) {
        ErpSignatureService.VerifyResult v = signatureService.verify(appId, ts, nonce, sig, "");
        if (v instanceof ErpSignatureService.Fail f) {
            return ResponseEntity.badRequest().body(fail(f.code(), f.message()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("version", "1.0");
        m.put("events", List.of("order.created", "order.paid", "order.shipped", "order.returned", "order.refunded", "order.cancelled"));
        m.put("required", List.of("event", "order_no", "occurred_at", "warehouse_code", "customer_code"));
        m.put("shipped_required", List.of("qty", "packages", "logistics.tracking_no"));
        m.put("signature", "hex(HMAC-SHA256(secret, X-Timestamp + \"\\n\" + X-Nonce + \"\\n\" + body))");
        m.put("doc", "docs/marketing/ERP接入契约.md");
        return ResponseEntity.ok(m);
    }

    private static Map<String, Object> ok(ErpOrderIngestService.IngestResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("order_id", r.orderId());
        m.put("attributed", r.attributed());
        return m;
    }

    private static Map<String, Object> fail(String code, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("code", code);
        m.put("msg", msg);
        return m;
    }
}
