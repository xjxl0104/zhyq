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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批链工作流接口:发起/通过/驳回、我的待办,以及流程定义/节点/实例/任务的基础查询。
 * 无 /api 前缀(全局 context-path 已是 /api)。
 *
 * 权限口径(ver6.6 补):本类此前无任何 @PreAuthorize,仅靠 SecurityConfig
 * 的 anyRequest().authenticated() 兜底,即任何登录用户可调。流程定义/节点
 * 是合同、采购等多个 bizType 共用的审批链配置,能改它就等于能改"谁能审批",
 * 可把审批人指向自己实现自审,故配置类接口一律要 workflow:definition:manage。
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

    // 定义/节点的读接口也一并收口:全项目只有 FlowConfig.vue(流程配置页)读它们,
    // 与写接口同页同权限位,不牵连运行时审批(task/instance 系列保持原样)。
    @Operation(summary = "流程定义分页")
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
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
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
    @GetMapping("/definition/{definitionId}/nodes")
    public Result<List<WfNode>> nodes(@PathVariable Long definitionId) {
        return Result.ok(nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getDefinitionId, definitionId)
                .orderByAsc(WfNode::getSeq)));
    }

    // ==================== 流程定义/节点配置 ====================

    @Operation(summary = "新增流程定义")
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
    @PostMapping("/definition")
    public Result<Long> addDefinition(@RequestBody WfDefinition definition) {
        definition.setId(null);
        if (definition.getStatus() == null) {
            definition.setStatus(1);
        }
        definitionMapper.insert(definition);
        return Result.ok(definition.getId());
    }

    @Operation(summary = "编辑流程定义(名称/启用状态)")
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
    @PutMapping("/definition")
    public Result<Void> updateDefinition(@RequestBody WfDefinition definition) {
        definitionMapper.updateById(definition);
        return Result.ok();
    }

    @Operation(summary = "删除流程定义(连同其节点)")
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
    @DeleteMapping("/definition/{id}")
    public Result<Void> removeDefinition(@PathVariable Long id) {
        nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getDefinitionId, id));
        definitionMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 整体保存某流程定义的审批节点:全删再按数组顺序重建,seq 按下标重排。
     * 已在途的审批实例按 seq 找节点,重建后 seq 语义不变,不影响其继续流转。
     */
    @Operation(summary = "保存流程节点(整体替换,按数组顺序重排 seq)")
    @PreAuthorize("hasAuthority('workflow:definition:manage')")
    @PutMapping("/definition/{definitionId}/nodes")
    public Result<Void> saveNodes(@PathVariable Long definitionId, @RequestBody List<WfNode> nodes) {
        nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getDefinitionId, definitionId));
        if (nodes != null) {
            int seq = 1;
            for (WfNode node : nodes) {
                node.setId(null);
                node.setDefinitionId(definitionId);
                node.setSeq(seq++);
                nodeMapper.insert(node);
            }
        }
        return Result.ok();
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
