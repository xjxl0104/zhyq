package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktCustomerPricingService;
import com.zhyq.park.marketing.service.MktCustomerPricingService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mp/v1/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpPricingController {
    private final MktCustomerPricingService pricingService;
    private final com.zhyq.park.marketing.mapper.MktPositionMapper positionMapper;

    @GetMapping("/positions")
    public Result<List<Map<String,String>>> positions() {
        return Result.ok(positionMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.zhyq.park.marketing.entity.MktPosition>()
                .in(com.zhyq.park.marketing.entity.MktPosition::getCode, List.of("P1","P2","P3","P4"))
                .orderByAsc(com.zhyq.park.marketing.entity.MktPosition::getSort))
                .stream().map(p -> Map.of("code",p.getCode(),"name",p.getName())).toList());
    }

    @GetMapping("/customers")
    public Result<List<PricingCustomer>> customers() {
        return Result.ok(pricingService.customers(MpAuthService.currentPromoterId()));
    }
    @GetMapping("/beneficiaries")
    public Result<List<Candidate>> beneficiaries() {
        return Result.ok(pricingService.beneficiaries(MpAuthService.currentPromoterId()));
    }
    @GetMapping("/team")
    public Result<List<Candidate>> team() {
        return Result.ok(pricingService.team(MpAuthService.currentPromoterId()));
    }
    @PutMapping("/team/{promoterId}/position")
    public Result<Void> setTeamPosition(@PathVariable Long promoterId, @RequestBody Map<String,String> input) {
        pricingService.setTeamPosition(MpAuthService.currentPromoterId(), promoterId, input.get("code"));
        return Result.ok();
    }
    @GetMapping("/customer/{customerId}")
    public Result<PricingView> get(@PathVariable Long customerId) {
        return Result.ok(pricingService.getForP4(MpAuthService.currentPromoterId(), customerId));
    }
    @PutMapping("/customer/{customerId}")
    public Result<PricingView> save(@PathVariable Long customerId, @RequestBody PricingInput input) {
        return Result.ok(pricingService.save(MpAuthService.currentPromoterId(), customerId, input));
    }
}
