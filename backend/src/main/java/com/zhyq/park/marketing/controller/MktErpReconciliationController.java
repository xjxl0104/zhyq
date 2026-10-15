package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktErpReconcileSnapshot;
import com.zhyq.park.marketing.open.ErpReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/crm/marketing/erp/warehouse/{warehouseId}/reconciliation")
@RequiredArgsConstructor
public class MktErpReconciliationController {
    private final ErpReconciliationService service;
    public record Confirmation(String reason) { }

    @GetMapping @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<ErpReconciliationService.Preview> preview(@PathVariable Long warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return Result.ok(service.preview(warehouseId, periodStart, periodEnd));
    }

    @PostMapping @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<MktErpReconcileSnapshot> submit(@PathVariable Long warehouseId, @RequestBody ErpReconciliationService.Submission body) {
        return Result.ok(service.submit(warehouseId, body));
    }

    @PostMapping("/{snapshotId}/confirm") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<MktErpReconcileSnapshot> confirm(@PathVariable Long warehouseId, @PathVariable Long snapshotId,
            @RequestBody Confirmation body, Authentication authentication) {
        return Result.ok(service.confirm(warehouseId, snapshotId, body.reason(), authentication.getName()));
    }

    @PostMapping("/refresh") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<Void> refresh(@PathVariable Long warehouseId, @RequestBody ErpReconciliationService.Submission body) {
        service.refreshPeriod(warehouseId, body.periodStart(), body.periodEnd());
        return Result.ok();
    }
}
