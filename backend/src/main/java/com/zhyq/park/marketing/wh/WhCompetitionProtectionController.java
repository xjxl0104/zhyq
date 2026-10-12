package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktCompetitionProtectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wh/v1/competition-protection")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WH')")
public class WhCompetitionProtectionController {
    private final MktCompetitionProtectionService service;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(service.list(WhAuthContext.currentWarehouseId()));
    }
    @PostMapping
    public Result<Void> add(@RequestBody Map<String, String> body) {
        service.add(WhAuthContext.currentWarehouseId(), body.get("name"));
        return Result.ok();
    }
    @PostMapping("/{id}/remove")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(WhAuthContext.currentWarehouseId(), id);
        return Result.ok();
    }
}
