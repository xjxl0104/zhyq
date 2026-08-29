package com.zhyq.park.budget.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.budget.entity.Budget;
import com.zhyq.park.budget.mapper.BudgetMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预算服务:编号生成 + 提交预算申请(走 #18 现成审批链引擎)+ 归档/取消。
 *
 * <p>状态:1草稿 2审批中 3已通过 4已驳回 5已归档 6已取消。
 * 「审批中→已通过/已驳回」不在本服务手工改,由审批链末节点通过/驳回后经
 * {@code BudgetWorkflowListener} 事件回调驱动,与合同/采购申请同一套做法。</p>
 *
 * <p>状态流转一律条件更新(update ... where id=? and status in (合法前态)),updated==0 抛业务异常。</p>
 *
 * <p>费用边界:amount 仅登记预算金额,绝不写 fin_bill / 触发收款。</p>
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    public static final int ST_DRAFT = 1;     // 草稿
    public static final int ST_AUDITING = 2;  // 审批中
    public static final int ST_APPROVED = 3;  // 已通过
    public static final int ST_REJECTED = 4;  // 已驳回
    public static final int ST_ARCHIVED = 5;  // 已归档
    public static final int ST_CANCELLED = 6; // 已取消

    /** 审批链业务类型;附件 bizType 同名,均为 budget */
    public static final String BIZ_TYPE = "budget";

    public static final int TYPE_ANNUAL = 1;  // 年度预算
    public static final int TYPE_MONTHLY = 2; // 月度预算

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BudgetMapper budgetMapper;
    private final WorkflowService workflowService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(Budget budget) {
        validate(budget);
        budget.setId(null);
        budget.setBudgetNo(genBudgetNo());
        budget.setStatus(ST_DRAFT);
        budget.setApprover(null);
        budget.setApproveTime(null);
        if (budget.getAmount() == null) {
            budget.setAmount(BigDecimal.ZERO);
        }
        budgetMapper.insert(budget);
        return budget.getId();
    }

    /** 编辑:仅草稿/已驳回可改。编号、状态、审批痕迹不可从外部改。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Budget budget) {
        if (budget.getId() == null) {
            throw new BizException("缺少预算ID");
        }
        validate(budget);
        int updated = budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, budget.getId())
                .in(Budget::getStatus, ST_DRAFT, ST_REJECTED)
                .set(Budget::getTitle, budget.getTitle())
                .set(Budget::getBudgetType, budget.getBudgetType())
                .set(Budget::getPeriod, budget.getPeriod())
                .set(Budget::getDepartment, budget.getDepartment())
                .set(Budget::getApplicant, budget.getApplicant())
                .set(Budget::getAmount, budget.getAmount() == null ? BigDecimal.ZERO : budget.getAmount())
                .set(Budget::getRemark, budget.getRemark()));
        if (updated == 0) {
            throw new BizException("仅草稿/已驳回状态可编辑,或预算不存在");
        }
    }

    /**
     * 提交预算申请:草稿/已驳回 → 审批中,并发起审批链。
     * 未配启用流程定义时 workflowService.start() 返回 null(降级),预算仍停在审批中,可由审批中心人工处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        int updated = budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, id)
                .in(Budget::getStatus, ST_DRAFT, ST_REJECTED)
                .set(Budget::getStatus, ST_AUDITING));
        if (updated == 0) {
            throw new BizException("仅草稿/已驳回状态可提交预算申请");
        }
        workflowService.start(BIZ_TYPE, id);
    }

    /** 审批链通过回调:审批中(2)→已通过(3)。由 BudgetWorkflowListener 调用,不对外暴露接口。 */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, id)
                .eq(Budget::getStatus, ST_AUDITING)
                .set(Budget::getStatus, ST_APPROVED)
                .set(Budget::getApprover, MyMetaObjectHandler.currentOperator())
                .set(Budget::getApproveTime, LocalDateTime.now()));
    }

    /** 审批链驳回回调:审批中(2)→已驳回(4)。 */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, id)
                .eq(Budget::getStatus, ST_AUDITING)
                .set(Budget::getStatus, ST_REJECTED)
                .set(Budget::getApprover, MyMetaObjectHandler.currentOperator())
                .set(Budget::getApproveTime, LocalDateTime.now()));
    }

    /** 归档(周期执行完毕):已通过(3)→已归档(5) */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        int updated = budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, id)
                .eq(Budget::getStatus, ST_APPROVED)
                .set(Budget::getStatus, ST_ARCHIVED));
        if (updated == 0) {
            throw new BizException("仅已通过状态可归档");
        }
    }

    /** 取消:草稿/审批中/已通过 → 已取消(6) */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        int updated = budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, id)
                .in(Budget::getStatus, ST_DRAFT, ST_AUDITING, ST_APPROVED)
                .set(Budget::getStatus, ST_CANCELLED));
        if (updated == 0) {
            throw new BizException("仅草稿/审批中/已通过状态可取消");
        }
    }

    /** 删除:审批中/已通过/已归档不可删,防误删在途或已执行的预算。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Budget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BizException("预算不存在");
        }
        if (budget.getStatus() == ST_AUDITING || budget.getStatus() == ST_APPROVED
                || budget.getStatus() == ST_ARCHIVED) {
            throw new BizException("审批中/已通过/已归档的预算不可删除,请先取消");
        }
        budgetMapper.deleteById(id);
    }

    public Budget detail(Long id) {
        Budget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BizException("预算不存在");
        }
        return budget;
    }

    private void validate(Budget budget) {
        if (budget.getBudgetType() == null
                || (budget.getBudgetType() != TYPE_ANNUAL && budget.getBudgetType() != TYPE_MONTHLY)) {
            throw new BizException("预算类型只能是年度或月度");
        }
        if (budget.getAmount() != null && budget.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("预算金额不能为负数");
        }
    }

    private String genBudgetNo() {
        String date = LocalDateTime.now().format(NO_FMT);
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return "BG-" + date + "-" + rand;
    }
}
