package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class QuoteAdviceController {
    private final QuoteAdviceService advice;

    @GetMapping("/crm/marketing/quote/customers/{id}/advice")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    public Result<Map<String,Object>> view(@PathVariable long id,@RequestParam Long projectId) {
        return Result.ok(advice.view(id,projectId));
    }

    @PostMapping("/crm/marketing/quote/customers/{id}/advice/retry")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    public Result<Map<String,Object>> retry(@PathVariable long id,@RequestParam Long projectId) {
        return Result.ok(advice.retry(id,projectId));
    }
}
