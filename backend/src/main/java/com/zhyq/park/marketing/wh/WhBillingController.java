package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.*;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import com.zhyq.park.marketing.settlement.MktWarehouseSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/wh/v1") @RequiredArgsConstructor
public class WhBillingController {
    private final MktServiceFeeBillMapper bills;
    private final MktServiceFeeBillLineMapper billLines;
    private final MktWarehouseSettlementLineMapper settlementLines;
    private final MktServiceFeeBillService billService;
    private final MktWarehouseSettlementService settlementService;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    @GetMapping("/bill/page") public Result<PageResult<MktServiceFeeBill>> bills(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        var p=bills.selectPage(new Page<>(Math.max(1,pageNo),Math.min(100,Math.max(1,pageSize))),new LambdaQueryWrapper<MktServiceFeeBill>()
                .eq(MktServiceFeeBill::getWarehouseId,WhAuthContext.currentWarehouseId()).orderByDesc(MktServiceFeeBill::getId));
        return Result.ok(PageResult.of(p.getTotal(),p.getRecords()));
    }
    @GetMapping("/bill/{id}/lines") public Result<List<MktServiceFeeBillLine>> billLines(@PathVariable Long id) {
        billService.requireOwned(id,WhAuthContext.currentWarehouseId());
        return Result.ok(billLines.selectList(new LambdaQueryWrapper<MktServiceFeeBillLine>().eq(MktServiceFeeBillLine::getBillId,id)));
    }
    @PostMapping("/bill/{id}/confirm") public Result<Void> confirmBill(@PathVariable Long id) { billService.confirm(id,WhAuthContext.currentWarehouseId());return Result.ok(); }
    @PostMapping("/bill/{id}/dispute") public Result<Void> disputeBill(@PathVariable Long id,@RequestBody Map<String,String> body) { billService.dispute(id,WhAuthContext.currentWarehouseId(),body.get("reason"));return Result.ok(); }
    @GetMapping("/settlement/{id}/lines") public Result<List<MktWarehouseSettlementLine>> settlementLines(@PathVariable Long id) {
        settlementService.requireOwned(id,WhAuthContext.currentWarehouseId());
        var safeLines = settlementLines.selectList(new LambdaQueryWrapper<MktWarehouseSettlementLine>().eq(MktWarehouseSettlementLine::getSettlementId,id));
        for (var line : safeLines) {
            // Merchant sees its own payable calculation, never internal revenue or partner commission.
            var safe = json.createObjectNode();
            try {
                var snapshot = json.readTree(line.getSnapshotJson());
                for (String key : List.of("perOrder", "perItem", "qty", "packages"))
                    if (snapshot != null && snapshot.has(key)) safe.set(key, snapshot.get(key));
            } catch (Exception ignored) { /* invalid legacy snapshots expose no internal fields */ }
            line.setSnapshotJson(safe.toString());
        }
        return Result.ok(safeLines);
    }
}
