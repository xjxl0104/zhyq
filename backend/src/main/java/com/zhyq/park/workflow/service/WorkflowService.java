package com.zhyq.park.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.workflow.entity.WfDefinition;
import com.zhyq.park.workflow.entity.WfInstance;
import com.zhyq.park.workflow.entity.WfNode;
import com.zhyq.park.workflow.entity.WfTask;
import com.zhyq.park.workflow.mapper.WfDefinitionMapper;
import com.zhyq.park.workflow.mapper.WfInstanceMapper;
import com.zhyq.park.workflow.mapper.WfNodeMapper;
import com.zhyq.park.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轻量自建审批链引擎(设计文档 §4)。单 @Service + 构造注入,不做接口+impl。
 *
 * <p>流转:start 建实例+首节点任务;approve 抢当前任务通过,有下一节点则推进,末节点则整体通过;
 * reject 抢当前任务驳回,实例整体驳回。状态流转一律用「条件 UPDATE 抢状态」防并发重复
 * (对齐 ContractService)。业务副作用不在此处,由监听器消费 WorkflowApproved 驱动
 * (见 workflow/listener/WorkflowCallbackListener),故本类不依赖 ContractService,无循环依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WfDefinitionMapper definitionMapper;
    private final WfNodeMapper nodeMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 定义状态
    private static final int DEF_ENABLED = 1;   // 启用

    // 实例状态
    private static final int INST_RUNNING = 1;  // 审批中
    private static final int INST_APPROVED = 2; // 通过
    private static final int INST_REJECTED = 3; // 驳回

    // 任务状态
    private static final int TASK_PENDING = 1;  // 待审
    private static final int TASK_APPROVED = 2; // 通过
    private static final int TASK_REJECTED = 3; // 驳回

    /**
     * 发起审批链。按 bizType 找启用的流程定义:
     * <ul>
     *   <li>无启用定义 → 直接返回(降级为旧单节点审批,保证未配流程也能跑);</li>
     *   <li>有定义但无节点 → 直接返回(异常配置降级,不阻塞业务);</li>
     *   <li>正常 → 建 wf_instance(current_seq=1,审批中) + 首节点 wf_task(待审),发 WorkflowTaskCreated。</li>
     * </ul>
     *
     * @param bizType     业务类型,如 contract
     * @param bizId       业务单据ID
     * @param approvalId  关联 biz_approval 单据头ID(D1-方案A,可为 null)
     * @return 新建实例ID;降级未起流程时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long start(String bizType, Long bizId, Long approvalId) {
        WfDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getBizType, bizType)
                .eq(WfDefinition::getStatus, DEF_ENABLED)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
        if (def == null) {
            log.info("[workflow] bizType={} 无启用流程定义,降级跳过审批链 bizId={}", bizType, bizId);
            return null;
        }

        WfNode firstNode = firstNodeOf(def.getId());
        if (firstNode == null) {
            log.warn("[workflow] definitionId={} 无节点配置,降级跳过审批链 bizId={}", def.getId(), bizId);
            return null;
        }

        WfInstance inst = new WfInstance();
        inst.setDefinitionId(def.getId());
        inst.setBizType(bizType);
        inst.setBizId(bizId);
        inst.setApprovalId(approvalId);
        inst.setCurrentSeq(firstNode.getSeq());
        inst.setStatus(INST_RUNNING);
        instanceMapper.insert(inst);

        createTask(inst, firstNode);
        return inst.getId();
    }

    /** 便捷重载:不关联 biz_approval 单据头。 */
    @Transactional(rollbackFor = Exception.class)
    public Long start(String bizType, Long bizId) {
        return start(bizType, bizId, null);
    }

    /**
     * 审批通过某任务:抢 task 待审→通过;再看是否还有下一节点:
     * 有 → 实例 current_seq 推进到下一节点、建新任务、发 WorkflowTaskCreated;
     * 无(末节点)→ 实例 审批中→通过、发 WorkflowApproved(回调驱动业务动作)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, String opinion) {
        WfTask task = requireTask(taskId);
        // 条件更新抢任务状态:仅当仍待审才生效,并发下只有一个 approve 成功
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getId, taskId)
                .eq(WfTask::getStatus, TASK_PENDING)
                .set(WfTask::getStatus, TASK_APPROVED)
                .set(WfTask::getOpinion, opinion)
                .set(WfTask::getActTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("该任务已被处理,无法重复审批");
        }

        WfInstance inst = requireRunningInstance(task.getInstanceId());
        WfNode nextNode = nextNodeOf(inst.getDefinitionId(), task.getSeq());
        if (nextNode != null) {
            // 推进到下一节点(条件更新抢实例 current_seq,防并发重复推进)
            int adv = instanceMapper.update(null, new LambdaUpdateWrapper<WfInstance>()
                    .eq(WfInstance::getId, inst.getId())
                    .eq(WfInstance::getStatus, INST_RUNNING)
                    .eq(WfInstance::getCurrentSeq, task.getSeq())
                    .set(WfInstance::getCurrentSeq, nextNode.getSeq()));
            if (adv == 0) {
                throw new BizException("审批实例状态已变更,推进失败");
            }
            inst.setCurrentSeq(nextNode.getSeq());
            createTask(inst, nextNode);
        } else {
            // 末节点:实例整体通过
            int done = instanceMapper.update(null, new LambdaUpdateWrapper<WfInstance>()
                    .eq(WfInstance::getId, inst.getId())
                    .eq(WfInstance::getStatus, INST_RUNNING)
                    .set(WfInstance::getStatus, INST_APPROVED));
            if (done == 0) {
                throw new BizException("审批实例状态已变更,通过失败");
            }
            // 发布通过事件:AFTER_COMMIT 回调驱动业务动作(contract → contractService.approve)
            eventPublisher.publishEvent(new DomainEvent.WorkflowApproved(
                    inst.getBizType(), inst.getBizId(), LocalDateTime.now()));
        }
    }

    /**
     * 驳回某任务:抢 task 待审→驳回 → 实例 审批中→驳回 → 发 WorkflowRejected。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, String opinion) {
        WfTask task = requireTask(taskId);
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<WfTask>()
                .eq(WfTask::getId, taskId)
                .eq(WfTask::getStatus, TASK_PENDING)
                .set(WfTask::getStatus, TASK_REJECTED)
                .set(WfTask::getOpinion, opinion)
                .set(WfTask::getActTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("该任务已被处理,无法重复驳回");
        }

        WfInstance inst = requireRunningInstance(task.getInstanceId());
        int done = instanceMapper.update(null, new LambdaUpdateWrapper<WfInstance>()
                .eq(WfInstance::getId, inst.getId())
                .eq(WfInstance::getStatus, INST_RUNNING)
                .set(WfInstance::getStatus, INST_REJECTED));
        if (done == 0) {
            throw new BizException("审批实例状态已变更,驳回失败");
        }
        eventPublisher.publishEvent(new DomainEvent.WorkflowRejected(
                inst.getBizType(), inst.getBizId(), LocalDateTime.now()));
    }

    // ==================== 内部辅助 ====================

    /** 建节点任务并发布 WorkflowTaskCreated(→ 待办)。 */
    private void createTask(WfInstance inst, WfNode node) {
        WfTask task = new WfTask();
        task.setInstanceId(inst.getId());
        task.setNodeId(node.getId());
        task.setSeq(node.getSeq());
        task.setAssignee(node.getApproverValue()); // 占位约定值,真实指派待 #7
        task.setStatus(TASK_PENDING);
        taskMapper.insert(task);

        eventPublisher.publishEvent(new DomainEvent.WorkflowTaskCreated(
                inst.getBizType(), inst.getBizId(), task.getId(),
                node.getName(), task.getAssignee(), LocalDateTime.now()));
    }

    private WfNode firstNodeOf(Long definitionId) {
        return nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getDefinitionId, definitionId)
                .orderByAsc(WfNode::getSeq)
                .last("limit 1"));
    }

    /** 找 seq 严格大于 currentSeq 的下一个节点;无则返回 null(末节点)。 */
    private WfNode nextNodeOf(Long definitionId, Integer currentSeq) {
        return nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getDefinitionId, definitionId)
                .gt(WfNode::getSeq, currentSeq)
                .orderByAsc(WfNode::getSeq)
                .last("limit 1"));
    }

    private WfTask requireTask(Long taskId) {
        WfTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("审批任务不存在");
        }
        return task;
    }

    private WfInstance requireRunningInstance(Long instanceId) {
        WfInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        if (inst.getStatus() != null && inst.getStatus() != INST_RUNNING) {
            throw new BizException("审批实例非审批中状态,无法处理");
        }
        return inst;
    }

    /** 我的待办:按 assignee 查待审任务(#7 前 assignee 为约定值)。 */
    public List<WfTask> myTasks(String assignee) {
        LambdaQueryWrapper<WfTask> qw = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getStatus, TASK_PENDING)
                .orderByDesc(WfTask::getId);
        if (assignee != null && !assignee.isBlank()) {
            qw.eq(WfTask::getAssignee, assignee);
        }
        return taskMapper.selectList(qw);
    }
}
