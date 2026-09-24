package com.zhyq.park.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.workflow.entity.WfInstance;
import com.zhyq.park.workflow.entity.WfNode;
import com.zhyq.park.workflow.entity.WfTask;
import com.zhyq.park.workflow.mapper.WfNodeMapper;
import com.zhyq.park.workflow.mapper.WfTaskMapper;
import com.zhyq.park.workflow.mapper.WorkflowBusinessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime authorization is separate from permission to configure workflow definitions. */
@Service
@RequiredArgsConstructor
public class WorkflowAccessService {
    private final WfNodeMapper nodes;
    private final WfTaskMapper tasks;
    private final WorkflowBusinessMapper business;
    private final com.zhyq.park.workflow.mapper.WfDefinitionMapper definitions;
    private final com.zhyq.park.workflow.mapper.WfInstanceMapper instances;
    private static final Map<String, String> RESOURCES = Map.of(
            "contract", "contract", "budget", "budget", "procurement", "pur:request");

    public void requireStart(String type, Long id, Long approvalId) {
        Authentication auth = current();
        String resource = RESOURCES.get(type);
        if (resource == null || (!admin(auth) && !has(auth, resource + ":submit"))) throw denied();
        if (id == null || id <= 0) throw new BizException("业务单据不存在");
        Integer status = switch (type) {
            case "contract" -> business.lockContractStatus(id);
            case "budget" -> business.lockBudgetStatus(id);
            case "procurement" -> business.lockProcurementStatus(id);
            default -> null;
        };
        if (!Integer.valueOf(2).equals(status)) throw new BizException("请先通过业务单据的提交审批操作发起流程");
        if (approvalId != null && business.countMatchingApproval(approvalId, type, id) != 1)
            throw new BizException(403, "审批单不属于当前业务或已不在审批中");
    }

    /** Only external legacy/manual approval entry points use this guard, never the synchronous workflow callback. */
    public void requireDirectApproval(String type, Long id) {
        Authentication auth = current();
        if (!admin(auth) && !("contract".equals(type) && has(auth, "contract:approve"))) throw denied();
        if (definitions.countEnabledWithNodes(type) > 0
                || instances.selectCount(new LambdaQueryWrapper<WfInstance>().eq(WfInstance::getBizType, type)
                    .eq(WfInstance::getBizId, id).eq(WfInstance::getStatus, 1)) > 0)
            throw new BizException("该业务有审批链，请从当前审批待办办理，不能跳过审批节点");
    }

    public void scopeApprovalHeaders(LambdaQueryWrapper<com.zhyq.park.oa.entity.Approval> query) {
        Authentication auth = current();
        if (admin(auth)) return;
        List<String> types = RESOURCES.entrySet().stream().filter(e -> has(auth, e.getValue() + ":query"))
                .map(Map.Entry::getKey).toList();
        if (types.isEmpty()) throw denied();
        query.in(com.zhyq.park.oa.entity.Approval::getBizType, types);
    }

    /** Read-only UI hint; performing an action must still call requireDirectApproval in its transaction. */
    public boolean canDirectApproval(String type, Long id) {
        try { requireDirectApproval(type, id); return true; }
        catch (BizException denied) { return false; }
    }

    public void requireApprovalAdministration() {
        if (!admin(current())) throw denied();
    }

    public void completeApprovalHeader(WfInstance instance, boolean approved, String opinion) {
        if (instance.getApprovalId() == null) return;
        if (business.completeApproval(instance.getApprovalId(), instance.getBizType(), instance.getBizId(),
                approved ? 3 : 4, current().getName(), opinion) != 1)
            throw new BizException("关联审批单状态已变化，请刷新后重试");
    }

    public void requireAssignee(WfTask task) {
        Authentication auth = current();
        if (admin(auth)) return;
        if (task == null || task.getAssignee() == null || task.getAssignee().isBlank()) throw denied();
        WfNode node = nodes.selectHistoricalApprovalNode(task.getNodeId());
        if (node == null) throw denied();
        boolean assigned = switch (String.valueOf(node.getApproverType())) {
            case "user" -> task.getAssignee().equals(auth.getName());
            case "role" -> has(auth, "ROLE_" + task.getAssignee());
            default -> false;
        };
        if (!assigned) throw denied();
    }

    public List<WfTask> myTasks(String requestedAssignee) {
        Authentication auth = current();
        List<String> roles = roles(auth);
        boolean admin = admin(auth);
        if (!admin && requestedAssignee != null && !requestedAssignee.isBlank()
                && !requestedAssignee.equals(auth.getName()) && !roles.contains(requestedAssignee)) throw denied();
        // The legacy UI sends the current username. That must not hide tasks assigned to one of their roles.
        String filter = admin ? requestedAssignee : null;
        return tasks.selectAuthorizedPending(auth.getName(), roles, admin, filter);
    }

    /** Scope before pagination, including its total count; never fetch an unrestricted page and filter afterward. */
    public void scopeInstances(LambdaQueryWrapper<WfInstance> query) {
        Authentication auth = current();
        if (admin(auth)) return;
        List<String> types = RESOURCES.entrySet().stream().filter(e -> has(auth, e.getValue() + ":query"))
                .map(Map.Entry::getKey).toList();
        List<Long> ids = tasks.selectParticipatingInstanceIds(auth.getName(), roles(auth));
        if (types.isEmpty() && ids.isEmpty()) throw denied();
        query.and(q -> {
            if (!types.isEmpty()) q.in(WfInstance::getBizType, types);
            if (!types.isEmpty() && !ids.isEmpty()) q.or();
            if (!ids.isEmpty()) q.in(WfInstance::getId, ids);
        });
    }

    public void requireVisible(WfInstance instance) {
        Authentication auth = current();
        if (instance == null) throw denied();
        if (admin(auth)) return;
        String resource = RESOURCES.get(instance.getBizType());
        if ((resource != null && has(auth, resource + ":query")) || Objects.equals(instance.getCreateBy(), auth.getName())) return;
        if (!tasks.selectParticipatingInstanceIds(auth.getName(), roles(auth)).contains(instance.getId())) throw denied();
    }

    public void validateNodes(List<WfNode> list) {
        if (list == null) return;
        for (WfNode node : list) {
            if (node == null || !("role".equals(node.getApproverType()) || "user".equals(node.getApproverType()))
                    || node.getApproverValue() == null || node.getApproverValue().isBlank()
                    || node.getApproverValue().length() > 64) throw new BizException("审批节点必须指定用户或角色");
        }
    }

    private static Authentication current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())
                || has(auth, "ROLE_MP") || has(auth, "ROLE_WH")) throw denied();
        return auth;
    }
    private static boolean admin(Authentication auth) { return has(auth, "ROLE_admin"); }
    private static boolean has(Authentication auth, String authority) { return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority())); }
    private static List<String> roles(Authentication auth) { return auth.getAuthorities().stream().map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).toList(); }
    private static BizException denied() { return new BizException(403, "无权操作或查看此审批流程"); }
}
