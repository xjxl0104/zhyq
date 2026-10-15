package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktErpReconcileSnapshot;
import com.zhyq.park.marketing.open.ErpReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** Warehouse users submit their own ERP export; only park ERP administrators confirm it. */
@RestController @RequestMapping("/wh/v1/erp/reconciliation") @RequiredArgsConstructor
public class WhErpReconciliationController {
    private final WhAuthContext auth;
    private final ErpReconciliationService service;

    @GetMapping public Result<ErpReconciliationService.Preview> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return Result.ok(service.preview(auth.requireWarehouse().getId(), periodStart, periodEnd));
    }
    @PostMapping public Result<MktErpReconcileSnapshot> submit(@RequestBody ErpReconciliationService.Submission body) {
        return Result.ok(service.submit(auth.requireWarehouse().getId(), body));
    }
}
