package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.*;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.settlement.MktWarehouseSettlementService;
import com.zhyq.park.marketing.service.MktAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/crm/marketing/settlement") @RequiredArgsConstructor
public class MktWarehouseSettlementController {
    private final MktWarehouseSettlementMapper settlements;
    private final MktWarehouseSettlementLineMapper lines;
    private final MktDirectSignPaymentMapper directPayments;
    private final MktWarehouseSettlementService service;
    public record Generate(Long warehouseId, LocalDate periodStart, LocalDate periodEnd) {}
    public record Pay(String payNo,String payProof,BigDecimal amount) {}
    public record DirectPayment(Long contractId,Long warehouseId,String paymentNo,BigDecimal amount,String payProof,LocalDate periodStart,LocalDate periodEnd) {}
    public record Reason(String reason) {}
    @GetMapping("/page") @PreAuthorize("hasAuthority('crm:marketing:settlement:query')")
    public Result<PageResult<MktWarehouseSettlement>> page(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) Long warehouseId,@RequestParam(required=false) Integer status) {
        var p=settlements.selectPage(new Page<>(Math.max(1,pageNo),Math.min(100,Math.max(1,pageSize))),new LambdaQueryWrapper<MktWarehouseSettlement>()
                .eq(warehouseId!=null,MktWarehouseSettlement::getWarehouseId,warehouseId).eq(status!=null,MktWarehouseSettlement::getStatus,status).orderByDesc(MktWarehouseSettlement::getId));
        return Result.ok(PageResult.of(p.getTotal(),p.getRecords()));
    }
    @PostMapping("/generate") @PreAuthorize("hasAuthority('crm:marketing:settlement:edit')")
    public Result<MktWarehouseSettlement> generate(@RequestBody Generate body) { return Result.ok(service.generate(body.warehouseId(),body.periodStart(),body.periodEnd())); }
    @GetMapping("/{id}/lines") @PreAuthorize("hasAuthority('crm:marketing:settlement:query')")
    public Result<List<MktWarehouseSettlementLine>> lines(@PathVariable Long id) { service.requireOwned(id,null);return Result.ok(lines.selectList(new LambdaQueryWrapper<MktWarehouseSettlementLine>().eq(MktWarehouseSettlementLine::getSettlementId,id))); }
    @PostMapping("/{id}/resolve") @PreAuthorize("hasAuthority('crm:marketing:settlement:edit')")
    public Result<Void> resolve(@PathVariable Long id,@RequestBody Reason body) { service.resolve(id,body.reason());return Result.ok(); }
    @PostMapping("/{id}/pay") @PreAuthorize("hasAuthority('crm:marketing:settlement:pay')")
    public Result<Void> pay(@PathVariable Long id,@RequestBody Pay body) { service.pay(id,body.payNo(),body.payProof(),body.amount(),MktAuditService.currentOperator());return Result.ok(); }
    @PostMapping("/direct-payment") @PreAuthorize("hasAuthority('crm:marketing:bill:pay')")
    public Result<MktDirectSignPayment> directPayment(@RequestBody DirectPayment body) { return Result.ok(service.recordPayment(body.contractId(),body.warehouseId(),body.paymentNo(),body.amount(),body.payProof(),body.periodStart(),body.periodEnd())); }
    @GetMapping("/direct-payment/page") @PreAuthorize("hasAuthority('crm:marketing:bill:query')")
    public Result<PageResult<MktDirectSignPayment>> directPayments(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        var p=directPayments.selectPage(new Page<>(Math.max(1,pageNo),Math.min(100,Math.max(1,pageSize))),new LambdaQueryWrapper<MktDirectSignPayment>().orderByDesc(MktDirectSignPayment::getId));
        return Result.ok(PageResult.of(p.getTotal(),p.getRecords()));
    }
}
