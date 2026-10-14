package com.zhyq.park.finance.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.service.FinanceOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "财务-总览")
@RestController
@RequestMapping("/finance/report")
@RequiredArgsConstructor
public class FinanceOverviewController {

    private final FinanceOverviewService overviewService;

    @Operation(summary = "财务总览：全部项目的账期应收、当月到账与当前待收")
    @PreAuthorize("hasAuthority('finance:report:query')")
    @GetMapping("/overview")
    public Result<FinanceOverviewService.Overview> overview(
            @RequestParam(required = false) String month) {
        return Result.ok(overviewService.overview(month));
    }
}
