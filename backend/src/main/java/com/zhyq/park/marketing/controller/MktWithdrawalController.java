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
    private final com.zhyq.park.marketing.service.MktWithdrawalViewService views;
    private final com.zhyq.park.marketing.service.MktWithdrawalApprovalIdentity approvalIdentity;

    @PreAuthorize("hasRole('admin')") @GetMapping("/approval-identity")
    public Result<Map<String,Object>> approvalIdentity() { return Result.ok(approvalIdentity.configuration()); }

    public record ApprovalIdentityRequest(String username, Long promoterId) {}
    @PreAuthorize("hasRole('admin')") @PutMapping("/approval-identity")
    public Result<Void> configureApprovalIdentity(@RequestBody ApprovalIdentityRequest request) {
        approvalIdentity.configure(request.username(),request.promoterId()); return Result.ok();
    }

    @Operation(summary = "分页") @PreAuthorize("isAuthenticated()") @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long projectId) {
        views.requireConsoleRead();
        LambdaQueryWrapper<MktWithdrawal> qw = new LambdaQueryWrapper<MktWithdrawal>()
                .eq(promoterId != null, MktWithdrawal::getPromoterId, promoterId)
                .eq(status != null, MktWithdrawal::getStatus, status)
                .and(projectId != null, q -> q.eq(MktWithdrawal::getProjectId, projectId).or().isNull(MktWithdrawal::getProjectId))
                .orderByDesc(MktWithdrawal::getId);
        IPage<MktWithdrawal> p = withdrawalMapper.selectPage(new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 50)), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(w -> views.describe(w, false)).toList()));
    }
    @Operation(summary = "提现详情与审批轨迹") @PreAuthorize("isAuthenticated()") @GetMapping("/{id}")
    public Result<Map<String, Object>> one(@PathVariable Long id) { return Result.ok(views.one(id, null)); }

    @Operation(summary = "代申请伙伴搜索") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @GetMapping("/manual-options")
    public Result<List<Map<String, Object>>> manualOptions(@RequestParam(required = false) String keyword) { return Result.ok(views.options(keyword)); }

    @Operation(summary = "代申请核对资料") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @GetMapping("/manual-context/{promoterId}")
    public Result<Map<String, Object>> manualContext(@PathVariable Long promoterId) { return Result.ok(views.manualContext(promoterId)); }

    @Operation(summary = "可提现余额") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:query')") @GetMapping("/balance/{promoterId}")
    public Result<BigDecimal> balance(@PathVariable Long promoterId) { return Result.ok(withdrawalService.balance(promoterId)); }

    @Operation(summary = "代伙伴申请(阶段 A)") @PreAuthorize("hasAuthority('crm:marketing:withdrawal:audit')") @PostMapping("/manual")
    public Result<MktWithdrawal> manual(@RequestBody Map<String, Object> body) {
        return Result.ok(withdrawalService.applyManual(Long.valueOf(body.get("promoterId").toString()), new BigDecimal(body.get("amount").toString())));
    }

    @Operation(summary = "审核通过") @PreAuthorize("isAuthenticated()") @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        withdrawalService.approveFromConsole(id, body == null ? null : body.get("opinion"), MktAuditService.currentOperator()); return Result.ok();
    }

    @Operation(summary = "驳回") @PreAuthorize("isAuthenticated()") @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        withdrawalService.rejectFromConsole(id, body.get("reason"), MktAuditService.currentOperator()); return Result.ok();
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
