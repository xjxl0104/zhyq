package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktSettleBatch;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
