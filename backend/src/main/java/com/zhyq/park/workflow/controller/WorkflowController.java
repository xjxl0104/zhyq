package com.zhyq.park.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.workflow.entity.WfDefinition;
import com.zhyq.park.workflow.entity.WfInstance;
import com.zhyq.park.workflow.entity.WfNode;
import com.zhyq.park.workflow.entity.WfTask;
import com.zhyq.park.workflow.mapper.WfDefinitionMapper;
import com.zhyq.park.workflow.mapper.WfInstanceMapper;
import com.zhyq.park.workflow.mapper.WfNodeMapper;
import com.zhyq.park.workflow.mapper.WfTaskMapper;
import com.zhyq.park.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批链工作流接口:发起/通过/驳回、我的待办,以及流程定义/节点/实例/任务的基础查询。
 * 流程配置本期先用 SQL 维护(见 V18 样例定义),这里提供查询与运行时操作。
 * 无 /api 前缀(全局 context-path 已是 /api)。
 */
@Tag(name = "审批链工作流")
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WfDefinitionMapper definitionMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;

    // ==================== 运行时操作 ====================

    @Operation(summary = "发起审批链(未配启用流程则降级返回 null)")
    @PostMapping("/start")
    public Result<Long> start(@RequestParam String bizType,
                              @RequestParam Long bizId,
                              @RequestParam(required = false) Long approvalId) {
        return Result.ok(workflowService.start(bizType, bizId, approvalId));
    }

    @Operation(summary = "审批通过某任务")
    @PostMapping("/task/{taskId}/approve")
    public Result<Void> approve(@PathVariable Long taskId, @RequestBody(required = false) OpinionReq req) {
        workflowService.approve(taskId, req == null ? null : req.getOpinion());
        return Result.ok();
    }

    @Operation(summary = "驳回某任务")
    @PostMapping("/task/{taskId}/reject")
    public Result<Void> reject(@PathVariable Long taskId, @RequestBody(required = false) OpinionReq req) {
        workflowService.reject(taskId, req == null ? null : req.getOpinion());
        return Result.ok();
    }

    @Operation(summary = "我的审批待办(按 assignee 约定值过滤,#7 前 assignee 多为角色码)")
    @GetMapping("/task/my")
    public Result<List<WfTask>> myTasks(@RequestParam(required = false) String assignee) {
        return Result.ok(workflowService.myTasks(assignee));
    }

    // ==================== 流程定义/节点查询 ====================

    @Operation(summary = "流程定义分页")
    @GetMapping("/definition/page")
    public Result<PageResult<WfDefinition>> definitionPage(@RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "10") int pageSize,
                                                           @RequestParam(required = false) String bizType,
                                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<WfDefinition> qw = new LambdaQueryWrapper<WfDefinition>()
                .eq(StringUtils.hasText(bizType), WfDefinition::getBizType, bizType)
                .eq(status != null, WfDefinition::getStatus, status)
                .orderByDesc(WfDefinition::getId);
        IPage<WfDefinition> p = definitionMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "某流程定义的节点列表(按 seq 升序)")
    @GetMapping("/definition/{definitionId}/nodes")
    public Result<List<WfNode>> nodes(@PathVariable Long definitionId) {
        return Result.ok(nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getDefinitionId, definitionId)
                .orderByAsc(WfNode::getSeq)));
    }

    // ==================== 实例/任务查询 ====================

    @Operation(summary = "审批实例分页")
    @GetMapping("/instance/page")
    public Result<PageResult<WfInstance>> instancePage(@RequestParam(defaultValue = "1") int pageNo,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) String bizType,
                                                       @RequestParam(required = false) Long bizId,
                                                       @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<WfInstance> qw = new LambdaQueryWrapper<WfInstance>()
                .eq(StringUtils.hasText(bizType), WfInstance::getBizType, bizType)
                .eq(bizId != null, WfInstance::getBizId, bizId)
                .eq(status != null, WfInstance::getStatus, status)
                .orderByDesc(WfInstance::getId);
        IPage<WfInstance> p = instanceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "某实例的任务列表(审批轨迹,按 seq 升序)")
    @GetMapping("/instance/{instanceId}/tasks")
    public Result<List<WfTask>> instanceTasks(@PathVariable Long instanceId) {
        return Result.ok(taskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instanceId)
                .orderByAsc(WfTask::getSeq)));
    }

    /** 审批意见入参。 */
    @Data
    public static class OpinionReq {
        private String opinion;
    }
}
