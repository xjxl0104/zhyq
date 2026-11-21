package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktWithdrawal;
import com.zhyq.park.marketing.mapper.MktWithdrawalMapper;
import com.zhyq.park.marketing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 伙伴身份仅能办理显式绑定给自己的提现审批，不开放员工通用审批接口。 */
@RestController
@RequestMapping("/mp/v1/withdrawal")
@RequiredArgsConstructor
public class MpWithdrawalApprovalController {
    private final MktWithdrawalApprovalIdentity identity;
    private final MktWithdrawalViewService views;
    private final MktWithdrawalMapper withdrawals;
    private final MktWithdrawalService service;

    @GetMapping("/approval/capability")
    public Result<Map<String,Object>> capability() {
        var user = identity.forPromoter(MpAuthService.currentPromoterId());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("canApproveWithdrawals",user != null);
        result.put("approverName",user == null ? null : user.getNickname());
        return Result.ok(result);
    }

    @GetMapping("/approval/page")
    public Result<PageResult<Map<String,Object>>> approvals(@RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) Integer status) {
        return identity.asApprover(MpAuthService.currentPromoterId(), () -> {
            var page = withdrawals.selectPage(new Page<MktWithdrawal>(Math.max(1,pageNo),Math.min(50,Math.max(1,pageSize))),
                    new LambdaQueryWrapper<MktWithdrawal>().eq(status != null,MktWithdrawal::getStatus,status).orderByDesc(MktWithdrawal::getId));
            return Result.ok(PageResult.of(page.getTotal(),page.getRecords().stream().map(w -> views.describe(w,false)).toList()));
        });
    }

    @GetMapping("/approval/{id}")
    public Result<Map<String,Object>> approval(@PathVariable Long id) {
        return identity.asApprover(MpAuthService.currentPromoterId(), () -> Result.ok(views.one(id,null)));
    }

    public record AuditRequest(Boolean approved, String remark) {}

    @PostMapping("/approval/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody AuditRequest request) {
        if (request == null || request.approved() == null) throw new BizException("请选择审批结果");
        if (request.remark() == null || request.remark().isBlank()) throw new BizException("请填写审批意见");
        if (request.remark().trim().length() > 500) throw new BizException("审批意见最多 500 字");
        return identity.asApprover(MpAuthService.currentPromoterId(), () -> {
            if (request.approved()) service.approveFromConsole(id,request.remark().trim(),MktAuditService.currentOperator());
            else service.rejectFromConsole(id,request.remark().trim(),MktAuditService.currentOperator());
            return Result.ok();
        });
    }

    @GetMapping("/{id}")
    public Result<Map<String,Object>> own(@PathVariable Long id) {
        return Result.ok(views.one(id,MpAuthService.currentPromoterId()));
    }
}
