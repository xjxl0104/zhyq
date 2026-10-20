package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktSettleBatch;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "全民营销-佣金结算")
@RestController
@RequestMapping("/crm/marketing/commission")
@RequiredArgsConstructor
public class MktCommissionController {

    private final MktPromoterCommissionMapper commissionMapper;
    private final MktSettleBatchMapper batchMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktCommissionService commissionService;

    @Operation(summary = "分页") @PreAuthorize("hasAuthority('crm:marketing:commission:query')") @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long referralOrderId,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktPromoterCommission> qw = new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(promoterId != null, MktPromoterCommission::getPromoterId, promoterId)
                .eq(status != null, MktPromoterCommission::getStatus, status)
                .eq(referralOrderId != null, MktPromoterCommission::getReferralOrderId, referralOrderId)
                .and(projectId != null, q -> q.eq(MktPromoterCommission::getProjectId, projectId).or().isNull(MktPromoterCommission::getProjectId))
                .orderByDesc(MktPromoterCommission::getId);
        IPage<MktPromoterCommission> p = commissionMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        Map<Long, String> names = new HashMap<>();
        List<Map<String, Object>> rows = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId()); m.put("referralOrderId", c.getReferralOrderId()); m.put("promoterId", c.getPromoterId());
            m.put("withdrawnAmount", c.getWithdrawnAmount()); m.put("withdrawalAmount", c.getWithdrawalAmount());
            m.put("pricingId", c.getPricingId()); m.put("amountPerOrder", c.getAmountPerOrder()); m.put("orderCount", c.getOrderCount());
            m.put("positionCode", c.getPositionCode()); m.put("sharePct", c.getSharePct()); m.put("diffPct", c.getDiffPct());
            m.put("baseAmount", c.getBaseAmount()); m.put("rate", c.getRate()); m.put("amount", c.getAmount()); m.put("sign", c.getSign());
            m.put("status", c.getStatus()); m.put("unfreezeAt", c.getUnfreezeAt()); m.put("settleBatchId", c.getSettleBatchId());
            m.put("withdrawalId", c.getWithdrawalId()); m.put("voidReason", c.getVoidReason()); m.put("createTime", c.getCreateTime());
            m.put("promoterName", names.computeIfAbsent(c.getPromoterId(), id -> {
                MktPromoter pr = promoterMapper.selectById(id); return pr == null ? null : pr.getName();
            }));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "批量结算(可结算 → 已结算),返回批次号") @PreAuthorize("hasAuthority('crm:marketing:commission:pay')") @PostMapping("/settle")
    public Result<String> settle(@RequestBody Map<String, List<Long>> body) {
        return Result.ok(commissionService.settle(body.get("ids"), MktAuditService.currentOperator()));
    }

    @Operation(summary = "按合同月结:每月3-10号结算该合同上个月的出库单佣金") @PreAuthorize("hasAuthority('crm:marketing:commission:pay')") @PostMapping("/settle-contract")
    public Result<Map<String, Object>> settleContract(@RequestBody Map<String, Long> body) {
        Long contractId = body == null ? null : body.get("contractId");
        if (contractId == null) throw new BizException("请选择服务合同");
        MktServiceContract contract = contractMapper.selectById(contractId);
        if (contract == null) throw new BizException("服务合同不存在");
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() < 3 || today.getDayOfMonth() > 10)
            throw new BizException("按合同月结仅限每月 3-10 号操作");
        LocalDate periodStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate nextStart = periodStart.plusMonths(1);
        // 该合同上月出库单(含拆单后的全部佣金行),冻结期未到/已结算的行分开统计
        String inSql = "SELECT id FROM crm_referral_order WHERE deleted = 0 AND source_type = "
                + MktCommissionService.SOURCE_OUTBOUND + " AND source_id = " + contractId
                + " AND event_time >= '" + periodStart + " 00:00:00' AND event_time < '" + nextStart + " 00:00:00'";
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .inSql(MktPromoterCommission::getReferralOrderId, inSql)
                .eq(MktPromoterCommission::getSign, 1)
                .isNull(MktPromoterCommission::getWithdrawalId));
        List<Long> ids = new ArrayList<>();
        int frozen = 0, already = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (MktPromoterCommission c : rows) {
            if (Integer.valueOf(MktCommissionService.C_SETTLEABLE).equals(c.getStatus())) {
                ids.add(c.getId());
                amount = amount.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            } else if (Integer.valueOf(MktCommissionService.C_FROZEN).equals(c.getStatus())) {
                frozen++;
            } else if (Integer.valueOf(MktCommissionService.C_SETTLED).equals(c.getStatus())
                    || Integer.valueOf(MktCommissionService.C_WITHDRAWN).equals(c.getStatus())) {
                already++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("contractNo", contract.getContractNo());
        result.put("periodStart", periodStart.toString());
        result.put("periodEnd", nextStart.minusDays(1).toString());
        result.put("settleCount", ids.size());
        result.put("settleAmount", amount);
        result.put("frozenCount", frozen);
        result.put("alreadySettledCount", already);
        result.put("batchNo", ids.isEmpty() ? null
                : commissionService.settle(ids, MktAuditService.currentOperator()));
        return Result.ok(result);
    }

    @Operation(summary = "作废单条") @PreAuthorize("hasAuthority('crm:marketing:commission:edit')") @PostMapping("/{id}/void")
    public Result<Void> voidOne(@PathVariable Long id, @RequestBody Map<String, String> body) {
        commissionService.voidCommission(id, body.get("reason")); return Result.ok();
    }

    @Operation(summary = "结算批次") @PreAuthorize("hasAuthority('crm:marketing:commission:query')") @GetMapping("/batches")
    public Result<PageResult<MktSettleBatch>> batches(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        IPage<MktSettleBatch> p = batchMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktSettleBatch>().orderByDesc(MktSettleBatch::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
