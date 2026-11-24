package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktSuperOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mp/v1/admin/overview")
@RequiredArgsConstructor
public class MpSuperOverviewController {
    private final MktSuperOverviewService overview;

    @GetMapping
    public Result<Map<String,Object>> overview(@RequestParam String view,
            @RequestParam(required=false) Long selectionId, @RequestParam(defaultValue="p4") String group,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String startDate,
            @RequestParam(required=false) String endDate, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) {
        return Result.ok(overview.overview(view,selectionId,group,keyword,startDate,endDate,page,size));
    }

    @GetMapping("/customer/{id}")
    public Result<Map<String,Object>> customer(@PathVariable Long id,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate, @RequestParam(required=false) Long warehouseId) {
        return Result.ok(overview.customer(id,startDate,endDate,warehouseId));
    }
}
