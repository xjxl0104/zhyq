package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "全民营销-提现")
@RestController
@RequestMapping("/crm/marketing/withdrawal")
@RequiredArgsConstructor
public class MktWithdrawalController {

    private final MktWithdrawalMapper withdrawalMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktWithdrawalService withdrawalService;
    private final com.zhyq.park.receivable.service.FieldEncryptionService encryption;
    private final MktAuditService auditService;

    @Operation(summary = "分页") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:query')") @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktWithdrawal> qw = new LambdaQueryWrapper<MktWithdrawal>()
                .eq(promoterId != null, MktWithdrawal::getPromoterId, promoterId)
                .eq(status != null, MktWithdrawal::getStatus, status)
                .and(projectId != null, q -> q.eq(MktWithdrawal::getProjectId, projectId).or().isNull(MktWithdrawal::getProjectId))
                .orderByDesc(MktWithdrawal::getId);
        IPage<MktWithdrawal> p = withdrawalMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<Map<String, Object>> rows = p.getRecords().stream().map(w -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId()); m.put("withdrawalNo", w.getWithdrawalNo()); m.put("promoterId", w.getPromoterId());
            m.put("amount", w.getAmount()); m.put("taxMode", w.getTaxMode()); m.put("taxAmount", w.getTaxAmount()); m.put("netAmount", w.getNetAmount());
            m.put("payNo", w.getPayNo()); m.put("payMethod", w.getPayMethod()); m.put("status", w.getStatus());
            m.put("auditBy", w.getAuditBy()); m.put("auditAt", w.getAuditAt()); m.put("payBy", w.getPayBy()); m.put("payAt", w.getPayAt());
            m.put("payProof", w.getPayProof()); m.put("accountName", w.getAccountName());
            m.put("accountType", w.getAccountType()); m.put("accountTail", w.getAccountTail()); m.put("bankName", w.getBankName());
            m.put("accountVerifiedAt", w.getAccountVerifiedAt());
            m.put("rejectReason", w.getRejectReason()); m.put("createTime", w.getCreateTime());
            MktPromoter pr = promoterMapper.selectById(w.getPromoterId());
            m.put("promoterName", pr == null ? null : pr.getName());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "可提现余额") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:query')") @GetMapping("/balance/{promoterId}")
    public Result<BigDecimal> balance(@PathVariable Long promoterId) { return Result.ok(withdrawalService.balance(promoterId)); }

    @Operation(summary = "代伙伴申请(阶段 A)") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @PostMapping("/manual")
    public Result<MktWithdrawal> manual(@RequestBody Map<String, Object> body) {
        return Result.ok(withdrawalService.apply(Long.valueOf(body.get("promoterId").toString()), new BigDecimal(body.get("amount").toString())));
    }

    @Operation(summary = "审核通过") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) { withdrawalService.approve(id, MktAuditService.currentOperator()); return Result.ok(); }

    @Operation(summary = "驳回") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        withdrawalService.reject(id, body.get("reason"), MktAuditService.currentOperator()); return Result.ok();
    }

    @GetMapping("/{id}/pay-account") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:pay')")
    public Result<Map<String, Object>> payAccount(@PathVariable Long id) {
        MktWithdrawal w = withdrawalMapper.selectById(id);
        if (w == null || w.getAccountNoEnc() == null) throw new com.zhyq.park.common.exception.BizException("提现收款账户快照不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("name", w.getAccountName()); result.put("accountType", w.getAccountType());
        result.put("accountNo", encryption.decrypt(w.getAccountNoEnc())); result.put("bankName", w.getBankName());
        auditService.log("withdrawal.account.view", "withdrawal", id, "财务查看申请时收款账户");
        return Result.ok(result);
    }

    @Operation(summary = "标记已打款(payNo 幂等)") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:pay')") @PostMapping("/{id}/pay")
    public Result<MktWithdrawal> pay(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(withdrawalService.pay(id, body.get("payNo"), body.get("payProof"), MktAuditService.currentOperator()));
    }
}
