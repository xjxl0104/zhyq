package com.zhyq.park.property.service;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 工单状态流转:每步写一条 pm_work_order_log(action/operator/content),整体事务。
 * 状态:1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭 7已超时。
 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final ApplicationEventPublisher eventPublisher;

    public static final int ST_PENDING_DISPATCH = 1; // 待派单
    public static final int ST_PENDING_ACCEPT = 2;    // 待接单
    public static final int ST_PROCESSING = 3;        // 处理中
    public static final int ST_PENDING_VERIFY = 4;    // 待验收
    public static final int ST_DONE = 5;              // 已完成
    public static final int ST_CLOSED = 6;            // 已关闭

    // SLA 超时状态
    public static final int SLA_RESP_TIMEOUT = 1;     // 响应超时
    public static final int SLA_RESOLVE_TIMEOUT = 2;  // 解决超时

    // 解决代码常量
    public static final String RESOLUTION_REPAIRED = "REPAIRED";      // 已修复
    public static final String RESOLUTION_REPLACED = "REPLACED";      // 已更换
    public static final String RESOLUTION_REBOOT = "REBOOT";          // 重启恢复
    public static final String RESOLUTION_NO_ISSUE = "NO_ISSUE";      // 未见异常
    public static final String RESOLUTION_TRANSFERRED = "TRANSFERRED"; // 转外部
    public static final String RESOLUTION_OTHER = "OTHER";            // 其他

    private static final Set<String> RESOLUTION_CODES = Set.of(
            RESOLUTION_REPAIRED, RESOLUTION_REPLACED, RESOLUTION_REBOOT,
            RESOLUTION_NO_ISSUE, RESOLUTION_TRANSFERRED, RESOLUTION_OTHER);

    private WorkOrder require(Long id) {
        WorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new BizException("工单不存在");
        }
        return wo;
    }

    private void log(Long orderId, String action, String operator, String content) {
        WorkOrderLog l = new WorkOrderLog();
        l.setOrderId(orderId);
        l.setAction(action);
        l.setOperator(operator);
        l.setContent(content);
        workOrderLogMapper.insert(l);
    }

    /** 派单:待派单(1)→待接单(2),记录处理人 */
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long id, String assignee) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_PENDING_DISPATCH) {
            throw new BizException("仅待派单状态的工单可派单");
        }
        if (!StringUtils.hasText(assignee)) {
            throw new BizException("请指定处理人");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setAssignee(assignee);
        upd.setStatus(ST_PENDING_ACCEPT);
        workOrderMapper.updateById(upd);
        log(id, "派单", assignee, "派单给:" + assignee);
    }

    /** 接单:待接单(2)→处理中(3) */
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, String operator) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_PENDING_ACCEPT) {
            throw new BizException("仅待接单状态的工单可接单");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(ST_PROCESSING);
        workOrderMapper.updateById(upd);
        log(id, "接单", operator, "已接单");
    }

    /** 到场:记录到场时间(需处理中) */
    @Transactional(rollbackFor = Exception.class)
    public void arrive(Long id, String operator) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_PROCESSING) {
            throw new BizException("仅处理中的工单可登记到场");
        }
        LocalDateTime now = LocalDateTime.now();
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setArriveTime(now);
        workOrderMapper.updateById(upd);
        log(id, "到场", operator, "已到场:" + now);
    }

    /** 处理完成:处理中(3)→待验收(4),记录完成时间。兼容老调用,不带解决代码 */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, String operator, String content) {
        finish(id, operator, content, null);
    }

    /** 处理完成:处理中(3)→待验收(4),记录完成时间 + 可选标准化解决代码 */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, String operator, String content, String resolutionCode) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_PROCESSING) {
            throw new BizException("仅处理中的工单可提交完成");
        }
        if (resolutionCode != null && !RESOLUTION_CODES.contains(resolutionCode)) {
            throw new BizException("解决代码不合法: " + resolutionCode);
        }
        LocalDateTime now = LocalDateTime.now();
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(ST_PENDING_VERIFY);
        upd.setFinishTime(now);
        upd.setResolutionCode(resolutionCode);
        workOrderMapper.updateById(upd);
        log(id, "处理", operator, StringUtils.hasText(content) ? content : "处理完成");
    }

    /** 回访:完成(5)后的满意度回访,记回访时间/备注/评分 */
    @Transactional(rollbackFor = Exception.class)
    public void revisit(Long id, String operator, Integer score, String remark) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_DONE) {
            throw new BizException("仅已完成状态的工单可回访");
        }
        if (score != null && (score < 1 || score > 5)) {
            throw new BizException("满意度评分需在 1-5 之间");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setRevisitTime(LocalDateTime.now());
        upd.setRevisitRemark(remark);
        if (score != null) {
            upd.setScore(score);
        }
        workOrderMapper.updateById(upd);
        log(id, "回访", operator, "满意度回访:" + (score == null ? "-" : score)
                + (StringUtils.hasText(remark) ? ",备注:" + remark : ""));
    }

    /** 验收:待验收(4)→已完成(5),记录满意度评分 */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, String operator, Integer score) {
        WorkOrder wo = require(id);
        if (wo.getStatus() == null || wo.getStatus() != ST_PENDING_VERIFY) {
            throw new BizException("仅待验收状态的工单可验收");
        }
        if (score != null && (score < 1 || score > 5)) {
            throw new BizException("满意度评分需在 1-5 之间");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(ST_DONE);
        upd.setScore(score);
        workOrderMapper.updateById(upd);
        log(id, "验收", operator, "验收通过,满意度评分:" + (score == null ? "-" : score));
        eventPublisher.publishEvent(new DomainEvent.WorkOrderClosed(id, LocalDateTime.now()));
    }

    /** 关闭:→已关闭(6)。已完成(5)的工单已终态,不允许再关闭覆盖 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String operator, String content) {
        WorkOrder wo = require(id);
        if (wo.getStatus() != null && (wo.getStatus() == ST_CLOSED || wo.getStatus() == ST_DONE)) {
            throw new BizException("已完成或已关闭的工单不可再关闭");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(ST_CLOSED);
        workOrderMapper.updateById(upd);
        log(id, "关闭", operator, StringUtils.hasText(content) ? content : "工单关闭");
        eventPublisher.publishEvent(new DomainEvent.WorkOrderClosed(id, LocalDateTime.now()));
    }
}
