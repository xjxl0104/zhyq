package com.zhyq.park.marketing.quote.rate;

import com.zhyq.park.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class QuoteRateController {
    private final QuoteRateService service;

    @GetMapping("/crm/marketing/quote/warehouses/{id}/rate-card")
    @PreAuthorize("hasAuthority('crm:marketing:warehouse:query')")
    public Result<Map<String,Object>> get(@PathVariable long id,@RequestParam Long projectId){
        return Result.ok(service.adminCard(id,projectId));
    }
    @PutMapping("/crm/marketing/quote/warehouses/{id}/rate-card")
    @PreAuthorize("hasAuthority('crm:marketing:warehouse:edit')")
    public Result<Map<String,Object>> put(@PathVariable long id,@RequestParam Long projectId,@RequestBody QuoteRateService.Update update){
        return Result.ok(service.saveAdmin(id,projectId,update));
    }
    @GetMapping("/crm/marketing/quote/customers/{id}/comparison")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    public Result<Map<String,Object>> compare(@PathVariable long id,@RequestParam Long projectId){
        return Result.ok(service.comparison(id,projectId));
    }
    @GetMapping("/wh/v1/quote/rate-card")
    @PreAuthorize("hasRole('WH_OWNER')")
    public Result<Map<String,Object>> mine(){return Result.ok(service.ownerCard());}
    @PutMapping("/wh/v1/quote/rate-card")
    @PreAuthorize("hasRole('WH_OWNER')")
    public Result<Map<String,Object>> saveMine(@RequestBody QuoteRateService.Update update){
        return Result.ok(service.saveOwner(update));
    }
}
