package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.*;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.settlement.MktWarehouseSettlementService;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/crm/marketing/settlement") @RequiredArgsConstructor
public class MktWarehouseSettlementController {
    private final MktWarehouseSettlementMapper settlements;
    private final MktWarehouseSettlementLineMapper lines;
    private final MktDirectSignPaymentMapper directPayments;
    private final MktReferralOrderMapper orderMapper;
    private final MktYunshanDailyVolumeMapper volumeMapper;
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

    @Operation(summary = "票数对账:ERP(云山)发货票数 vs 系统计佣订单数") @PreAuthorize("hasAuthority('crm:marketing:settlement:query')")
    @GetMapping("/ticket-reconcile")
    public Result<Map<String, Object>> ticketReconcile(@RequestParam Long warehouseId, @RequestParam String month) {
        if (warehouseId == null) throw new BizException("请选择云仓");
        YearMonth ym;
        try { ym = YearMonth.parse(month); } catch (Exception e) { throw new BizException("月份格式应为 yyyy-MM"); }
        LocalDate start = ym.atDay(1), end = ym.atEndOfMonth();
        // ERP 侧:云山日发货票数合计(没有统计记录时返回 null,不解释为零)
        List<MktYunshanDailyVolume> volumes = volumeMapper.selectList(new LambdaQueryWrapper<MktYunshanDailyVolume>()
                .eq(MktYunshanDailyVolume::getWarehouseId, warehouseId)
                .ge(MktYunshanDailyVolume::getStatDate, start).le(MktYunshanDailyVolume::getStatDate, end));
        Long erpTickets = null; long erpDays = 0;
        if (!volumes.isEmpty()) {
            erpTickets = 0L;
            Set<LocalDate> days = new HashSet<>();
            for (MktYunshanDailyVolume v : volumes) {
                erpTickets += v.getConsignNum() == null ? 0 : v.getConsignNum();
                if (v.getStatDate() != null) days.add(v.getStatDate());
            }
            erpDays = days.size();
        }
        // 系统侧:该云仓当月出库类计佣订单按状态分解;服务费与佣金池全零的为直签运营记录
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getWarehouseId, warehouseId)
                .ge(MktReferralOrder::getEventTime, start.atStartOfDay())
                .lt(MktReferralOrder::getEventTime, end.plusDays(1).atStartOfDay()));
        int confirmed = 0, pending = 0, cancelled = 0, directSign = 0;
        for (MktReferralOrder o : orders) {
            Integer st = o.getStatus();
            if (Integer.valueOf(MktCommissionService.ORDER_CONFIRMED).equals(st)) confirmed++;
            else if (Integer.valueOf(MktCommissionService.ORDER_PENDING).equals(st)) pending++;
            else if (Integer.valueOf(MktCommissionService.ORDER_REFUNDED).equals(st)
                    || Integer.valueOf(MktCommissionService.ORDER_CANCELLED).equals(st)) cancelled++;
            boolean zero = (o.getServiceFee() == null || o.getServiceFee().signum() == 0)
                    && (o.getPoolAmount() == null || o.getPoolAmount().signum() == 0);
            if (zero) directSign++;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("month", month);
        m.put("erpTickets", erpTickets);
        m.put("erpDays", erpDays);
        m.put("systemOrders", orders.size());
        m.put("confirmed", confirmed);
        m.put("pending", pending);
        m.put("cancelled", cancelled);
        m.put("directSignOperating", directSign);
        m.put("diff", erpTickets == null ? null : erpTickets - orders.size());
        return Result.ok(m);
    }

    @PostMapping("/direct-payment") @PreAuthorize("hasAuthority('crm:marketing:bill:pay')")
    public Result<MktDirectSignPayment> directPayment(@RequestBody DirectPayment body) { return Result.ok(service.recordPayment(body.contractId(),body.warehouseId(),body.paymentNo(),body.amount(),body.payProof(),body.periodStart(),body.periodEnd())); }
    @GetMapping("/direct-payment/page") @PreAuthorize("hasAuthority('crm:marketing:bill:query')")
    public Result<PageResult<MktDirectSignPayment>> directPayments(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        var p=directPayments.selectPage(new Page<>(Math.max(1,pageNo),Math.min(100,Math.max(1,pageSize))),new LambdaQueryWrapper<MktDirectSignPayment>().orderByDesc(MktDirectSignPayment::getId));
        return Result.ok(PageResult.of(p.getTotal(),p.getRecords()));
    }
}
