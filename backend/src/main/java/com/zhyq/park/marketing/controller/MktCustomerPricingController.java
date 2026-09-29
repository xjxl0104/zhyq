package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktCustomerPricingService;
import com.zhyq.park.marketing.service.MktCustomerPricingService.PricingView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 管理端查看定价与对账，不因后台管理员身份授予 P4 定价权。 */
@RestController
@RequestMapping("/crm/marketing/customer")
@RequiredArgsConstructor
public class MktCustomerPricingController {
    private final MktCustomerPricingService pricingService;
    @GetMapping("/{customerId}/pricing")
    @PreAuthorize("hasAuthority('crm:marketing:customer:query')")
    public Result<PricingView> get(@PathVariable Long customerId) {
        return Result.ok(pricingService.getForAdmin(customerId));
    }
}
