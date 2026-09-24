package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.service.ContractService;
import com.zhyq.park.oa.entity.Approval;
import com.zhyq.park.oa.mapper.ApprovalMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.workflow.service.WorkflowAccessService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审批中心(规格书 §13):通用审批实例的查询与通过/驳回。
 * bizType=contract 时联动合同状态(oa 依赖 contract 单向,不反向)。
 */
@Tag(name = "办公管理-审批中心")
@RestController
@RequestMapping("/oa/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalMapper approvalMapper;
    private final ContractService contractService;
    private final ContractMapper contractMapper;
    private final WorkflowAccessService access;

    // 审批状态:1草稿 2审批中 3已通过 4已驳回 5已撤回 6已终止
    private static final int ST_PENDING = 2;
    private static final int ST_APPROVED = 3;
    private static final int ST_REJECTED = 4;

    // 合同状态(联动用)
    private static final int CONTRACT_DRAFT = 1;
    private static final int CONTRACT_AUDITING = 2;

    @Operation(summary = "分页查询审批单")
    @GetMapping("/page")
    public Result<PageResult<Approval>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) Long bizId,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String title) {
        LambdaQueryWrapper<Approval> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(bizType), Approval::getBizType, bizType)
          .eq(bizId != null, Approval::getBizId, bizId)
          .eq(status != null, Approval::getStatus, status)
          .like(StringUtils.hasText(title), Approval::getTitle, title)
          .orderByDesc(Approval::getId);
        access.scopeApprovalHeaders(qw);
        IPage<Approval> p = approvalMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        p.getRecords().forEach(approval -> approval.setCanDirectApprove(
                Integer.valueOf(ST_PENDING).equals(approval.getStatus())
                        && access.canDirectApproval(approval.getBizType(), approval.getBizId())));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "审批统计:待审批/已通过/已驳回/总数")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> map = new HashMap<>();
        for (var item : Map.of("pending", ST_PENDING, "approved", ST_APPROVED, "rejected", ST_REJECTED, "total", 0).entrySet()) {
            LambdaQueryWrapper<Approval> scope = new LambdaQueryWrapper<>();
            access.scopeApprovalHeaders(scope);
            if (item.getValue() != 0) scope.eq(Approval::getStatus, item.getValue());
            map.put(item.getKey(), approvalMapper.selectCount(scope));
        }
        return Result.ok(map);
    }

    /**
     * 审批通过:审批中(2)→已通过(3),条件更新抢状态,并发重复操作只有一次生效。
     * bizType=contract 时联动 ContractService.approve(生成账单+房源在租);
     * 已配置链的业务必须走实际指派待办；关联业务失败时审批单同事务回滚。
     */
    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) Map<String, String> body) {
        String opinion = body == null ? null : body.get("opinion");
        Approval a = approvalMapper.selectById(id);
        if (a == null) {
            throw new BizException("审批单不存在");
        }
        access.requireDirectApproval(a.getBizType(), a.getBizId());
        int updated = approvalMapper.update(null, new LambdaUpdateWrapper<Approval>()
                .eq(Approval::getId, id)
                .eq(Approval::getStatus, ST_PENDING)
                .set(Approval::getStatus, ST_APPROVED)
                .set(Approval::getApproveBy, MyMetaObjectHandler.currentOperator())
                .set(Approval::getApproveTime, LocalDateTime.now())
                .set(Approval::getOpinion, opinion));
        if (updated == 0) {
            throw new BizException("仅审批中的单据可操作");
        }
        // 联动:合同审批通过
        if ("contract".equals(a.getBizType()) && a.getBizId() != null) {
            contractService.approve(a.getBizId());
        }
        return Result.ok();
    }

    /**
     * 驳回:审批中(2)→已驳回(4),记录意见。
     * bizType=contract 时把合同从待审核(2)改回草稿(1),同样条件更新容错。
     */
    @Operation(summary = "审批驳回")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        String opinion = body == null ? null : body.get("opinion");
        Approval a = approvalMapper.selectById(id);
        if (a == null) {
            throw new BizException("审批单不存在");
        }
        access.requireDirectApproval(a.getBizType(), a.getBizId());
        int updated = approvalMapper.update(null, new LambdaUpdateWrapper<Approval>()
                .eq(Approval::getId, id)
                .eq(Approval::getStatus, ST_PENDING)
                .set(Approval::getStatus, ST_REJECTED)
                .set(Approval::getApproveBy, MyMetaObjectHandler.currentOperator())
                .set(Approval::getApproveTime, LocalDateTime.now())
                .set(Approval::getOpinion, opinion));
        if (updated == 0) {
            throw new BizException("仅审批中的单据可操作");
        }
        // 联动:合同退回草稿(条件更新,合同不在待审核则自然不生效)
        if ("contract".equals(a.getBizType()) && a.getBizId() != null) {
            int changed = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                    .eq(Contract::getId, a.getBizId())
                    .eq(Contract::getStatus, CONTRACT_AUDITING)
                    .set(Contract::getStatus, CONTRACT_DRAFT));
            if (changed != 1) throw new BizException("关联合同不在待审核状态，审批单未变更");
        }
        return Result.ok();
    }

    @Operation(summary = "新增审批单")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> add(@RequestBody Approval approval) {
        access.requireApprovalAdministration();
        if ("contract".equals(approval.getBizType())) throw new BizException("合同审批单由合同提交自动生成");
        approval.setId(null);
        if (approval.getStatus() == null) {
            approval.setStatus(ST_PENDING);
        }
        approvalMapper.insert(approval);
        return Result.ok(approval.getId());
    }

    @Operation(summary = "删除审批单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        access.requireApprovalAdministration();
        Approval approval = approvalMapper.selectById(id);
        if (approval != null && !Integer.valueOf(1).equals(approval.getStatus()))
            throw new BizException("审批中或已结束的审批单须保留记录，不可删除");
        approvalMapper.deleteById(id);
        return Result.ok();
    }
}
