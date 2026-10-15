package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.open.ErpExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/crm/marketing/erp/warehouse/{warehouseId}/exceptions")
@RequiredArgsConstructor
public class MktErpExceptionController {
    private final ErpExceptionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<PageResult<ErpExceptionService.ExceptionView>> page(@PathVariable Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.page(warehouseId, pageNo, pageSize));
    }

    public record RetryRequest(String reason) {}
    @PostMapping("/{inboxId}/retry")
    @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<Void> retry(@PathVariable Long warehouseId, @PathVariable Long inboxId, @RequestBody RetryRequest request) {
        service.retry(warehouseId, inboxId, request.reason());
        return Result.ok();
    }
}
