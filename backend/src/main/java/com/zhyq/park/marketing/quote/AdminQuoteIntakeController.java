package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminQuoteIntakeController {
    private final QuoteIntakeService intake;

    @GetMapping("/crm/marketing/customer/{id}/quote-intake")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    public Result<Map<String,Object>> state(@PathVariable long id,@RequestParam long projectId) {
        return Result.ok(intake.admin(id,projectId));
    }

    @PostMapping("/crm/marketing/customer/{id}/quote-intake/project")
    @PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
    public Result<Map<String,Object>> confirmProject(@PathVariable long id,@RequestParam long projectId) {
        return Result.ok(intake.confirmProject(id,projectId));
    }
}
