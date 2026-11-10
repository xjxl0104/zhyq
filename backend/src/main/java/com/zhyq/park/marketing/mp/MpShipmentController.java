package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktPartnerShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mp/v1/shipments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpShipmentController {
    private final MktPartnerShipmentService shipments;
    @GetMapping
    public Result<Map<String,Object>> report(@RequestParam(defaultValue="own") String scope,
            @RequestParam(required=false) String startDate, @RequestParam(required=false) String endDate,
            @RequestParam(required=false) Long memberId) {
        return Result.ok(shipments.report(MpAuthService.currentPromoterId(), scope, startDate, endDate, memberId));
    }
}
