package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktPublicSeaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 加盟云仓主账号专属：园区公海热单限时认领接口（每日限领 5 户 / 在途上限 10 户 / 72h 首触达保活）。
 */
@Tag(name = "云仓工作台-公海热单限时认领")
@RestController
@RequestMapping("/wh/v1/public-sea")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WH_OWNER')")
public class WhPublicSeaController {

    private final MktPublicSeaService publicSeaService;

    @Operation(summary = "查询可认领公海热单与本仓剩余配额")
    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(publicSeaService.warehousePool(pageNo, pageSize));
    }

    @Operation(summary = "限时认领公海热单(绑定本仓承接，免70%外部佣金)")
    @PostMapping("/{customerId}/claim")
    public Result<Map<String, Object>> claim(@PathVariable Long customerId) {
        return Result.ok(publicSeaService.claimByWarehouse(customerId));
    }

    @Operation(summary = "登记首通接洽反馈(解除72h自动弹回)")
    @PostMapping("/{customerId}/first-touch")
    public Result<Void> firstTouch(@PathVariable Long customerId, @RequestBody Map<String, String> body) {
        publicSeaService.recordWarehouseTouch(customerId, body.get("note"));
        return Result.ok();
    }
}
