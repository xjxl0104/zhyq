package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.*;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/crm/marketing/bill") @RequiredArgsConstructor
public class MktServiceFeeBillController {
    private final MktServiceFeeBillMapper bills;
    private final MktServiceFeeBillLineMapper lines;
    private final MktServiceFeeBillService service;
    private final com.zhyq.park.marketing.finance.MktFixedFeeBillingService fixedFees;
    public record Generate(Long contractId, LocalDate periodStart, LocalDate periodEnd) {}
    public record Receive(String receiptNo, String receiptProof, BigDecimal amount) {}
    public record Reason(String reason) {}
    @GetMapping("/page") @PreAuthorize("hasAuthority('crm:marketing:bill:query')")
    public Result<PageResult<MktServiceFeeBill>> page(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) Long warehouseId, @RequestParam(required=false) Long contractId, @RequestParam(required=false) Integer status) {
        var p = bills.selectPage(new Page<>(Math.max(1,pageNo),Math.min(100,Math.max(1,pageSize))), new LambdaQueryWrapper<MktServiceFeeBill>()
                .eq(warehouseId!=null,MktServiceFeeBill::getWarehouseId,warehouseId).eq(contractId!=null,MktServiceFeeBill::getContractId,contractId)
                .eq(status!=null,MktServiceFeeBill::getStatus,status).orderByDesc(MktServiceFeeBill::getId));
        return Result.ok(PageResult.of(p.getTotal(),p.getRecords()));
    }
    @PostMapping("/generate-fixed") @PreAuthorize("hasAuthority('crm:marketing:bill:edit')")
    public Result<Integer> generateFixed() { return Result.ok(fixedFees.generateDue(LocalDate.now())); }
    @PostMapping("/generate") @PreAuthorize("hasAuthority('crm:marketing:bill:edit')")
    public Result<MktServiceFeeBill> generate(@RequestBody Generate body) { return Result.ok(service.generate(body.contractId(),body.periodStart(),body.periodEnd())); }
    @GetMapping("/{id}/lines") @PreAuthorize("hasAuthority('crm:marketing:bill:query')")
    public Result<List<MktServiceFeeBillLine>> lines(@PathVariable Long id) { service.requireOwned(id,null); return Result.ok(lines.selectList(new LambdaQueryWrapper<MktServiceFeeBillLine>().eq(MktServiceFeeBillLine::getBillId,id))); }
    @PostMapping("/{id}/resolve") @PreAuthorize("hasAuthority('crm:marketing:bill:edit')")
    public Result<Void> resolve(@PathVariable Long id,@RequestBody Reason body) { service.resolve(id,body.reason()); return Result.ok(); }
    @PostMapping("/{id}/receive") @PreAuthorize("hasAuthority('crm:marketing:bill:pay')")
    public Result<Void> receive(@PathVariable Long id,@RequestBody Receive body) { service.receive(id,body.receiptNo(),body.receiptProof(),body.amount()); return Result.ok(); }
}
