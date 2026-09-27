package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktSelfProfileService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wh/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WH')")
public class WhMeController {
    private final MktSelfProfileService profiles;

    @Operation(summary = "本云仓个人信息") @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(profiles.warehouseProfile(WhAuthContext.currentWarehouseId()));
    }

    @Operation(summary = "补充本云仓个人信息，不变更审核状态") @PutMapping("/me")
    public Result<Map<String, Object>> updateMe(@RequestBody Map<String, String> body) {
        return Result.ok(profiles.updateWarehouse(WhAuthContext.currentWarehouseId(), body));
    }
}
