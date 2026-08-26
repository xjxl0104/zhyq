package com.zhyq.park.pur.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.pur.entity.PurRequest;
import com.zhyq.park.pur.entity.PurRequestItem;
import com.zhyq.park.pur.mapper.PurRequestItemMapper;
import com.zhyq.park.pur.mapper.PurRequestMapper;
import com.zhyq.park.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 采购申请服务:明细汇总 + 提交审批(走 #18 现成审批链引擎)+ 完成/取消。
 *
 * <p>状态:1草稿 2审批中 3已通过 4已驳回 5已完成 6已取消。
 * 「审批中→已通过/已驳回」不在本服务手工改,由审批链末节点通过/驳回后经
 * {@code PurWorkflowListener} 事件回调驱动,与合同模块同一套做法。</p>
 *
 * <p>状态流转一律条件更新(update ... where id=? and status in (合法前态)),updated==0 抛业务异常。</p>
 *
 * <p>费用边界:totalAmount 仅记录采购预算/实付金额,绝不写 fin_bill / 触发收款。</p>
 */
@Service
@RequiredArgsConstructor
public class PurRequestService {

    public static final int ST_DRAFT = 1;     // 草稿
    public static final int ST_AUDITING = 2;  // 审批中
    public static final int ST_APPROVED = 3;  // 已通过
    public static final int ST_REJECTED = 4;  // 已驳回
    public static final int ST_COMPLETED = 5; // 已完成
    public static final int ST_CANCELLED = 6; // 已取消

    public static final String BIZ_TYPE = "procurement";

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurRequestMapper requestMapper;
    private final PurRequestItemMapper itemMapper;
    private final WorkflowService workflowService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(PurRequest request) {
        request.setId(null);
        request.setRequestNo(genRequestNo());
        request.setStatus(ST_DRAFT);
        request.setApprover(null);
        request.setApproveTime(null);
        request.setTotalAmount(sumAndFillItems(request.getItems()));
        requestMapper.insert(request);
        saveItems(request.getId(), request.getItems());
        return request.getId();
    }

    /** 编辑:仅草稿/已驳回可改,整单替换明细。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(PurRequest request) {
        if (request.getId() == null) {
            throw new BizException("缺少采购申请ID");
        }
        BigDecimal total = sumAndFillItems(request.getItems());

        int updated = requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, request.getId())
                .in(PurRequest::getStatus, ST_DRAFT, ST_REJECTED)
                .set(PurRequest::getPlanId, request.getPlanId())
                .set(PurRequest::getTitle, request.getTitle())
                .set(PurRequest::getSupplier, request.getSupplier())
                .set(PurRequest::getApplicant, request.getApplicant())
                .set(PurRequest::getDepartment, request.getDepartment())
                .set(PurRequest::getSpaceId, request.getSpaceId())
                .set(PurRequest::getTotalAmount, total)
                .set(PurRequest::getRemark, request.getRemark()));
        if (updated == 0) {
            throw new BizException("仅草稿/已驳回状态可编辑,或采购申请不存在");
        }

        itemMapper.delete(new LambdaQueryWrapper<PurRequestItem>()
                .eq(PurRequestItem::getRequestId, request.getId()));
        saveItems(request.getId(), request.getItems());
    }

    /**
     * 提交审批:草稿/已驳回 → 审批中,并发起审批链。
     * 未配启用流程定义时 workflowService.start() 返回 null(降级),申请仍停在审批中,可由审批中心人工处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, id)
                .in(PurRequest::getStatus, ST_DRAFT, ST_REJECTED)
                .set(PurRequest::getStatus, ST_AUDITING));
        if (updated == 0) {
            throw new BizException("仅草稿/已驳回状态可提交审批");
        }
        workflowService.start(BIZ_TYPE, id);
    }

    /** 审批链通过回调:审批中(2)→已通过(3)。由 PurWorkflowListener 调用,不对外暴露接口。 */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, id)
                .eq(PurRequest::getStatus, ST_AUDITING)
                .set(PurRequest::getStatus, ST_APPROVED)
                .set(PurRequest::getApprover, MyMetaObjectHandler.currentOperator())
                .set(PurRequest::getApproveTime, LocalDateTime.now()));
    }

    /** 审批链驳回回调:审批中(2)→已驳回(4)。 */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, id)
                .eq(PurRequest::getStatus, ST_AUDITING)
                .set(PurRequest::getStatus, ST_REJECTED)
                .set(PurRequest::getApprover, MyMetaObjectHandler.currentOperator())
                .set(PurRequest::getApproveTime, LocalDateTime.now()));
    }

    /** 完成(到货入库确认):已通过(3)→已完成(5) */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, id)
                .eq(PurRequest::getStatus, ST_APPROVED)
                .set(PurRequest::getStatus, ST_COMPLETED));
        if (updated == 0) {
            throw new BizException("仅已通过状态可标记完成");
        }
    }

    /** 取消:草稿/审批中/已通过 → 已取消(6) */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<PurRequest>()
                .eq(PurRequest::getId, id)
                .in(PurRequest::getStatus, ST_DRAFT, ST_AUDITING, ST_APPROVED)
                .set(PurRequest::getStatus, ST_CANCELLED));
        if (updated == 0) {
            throw new BizException("仅草稿/审批中/已通过状态可取消");
        }
    }

    /** 删除:审批中/已通过/已完成不可删,防误删在途或已执行单据。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        PurRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException("采购申请不存在");
        }
        if (request.getStatus() == ST_AUDITING || request.getStatus() == ST_APPROVED
                || request.getStatus() == ST_COMPLETED) {
            throw new BizException("审批中/已通过/已完成的采购申请不可删除,请先取消");
        }
        itemMapper.delete(new LambdaQueryWrapper<PurRequestItem>().eq(PurRequestItem::getRequestId, id));
        requestMapper.deleteById(id);
    }

    public PurRequest detail(Long id) {
        PurRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException("采购申请不存在");
        }
        request.setItems(itemMapper.selectList(
                new LambdaQueryWrapper<PurRequestItem>().eq(PurRequestItem::getRequestId, id)));
        return request;
    }

    private BigDecimal sumAndFillItems(List<PurRequestItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("请至少添加一条采购明细");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurRequestItem item : items) {
            if (!StringUtils.hasText(item.getItemName())) {
                throw new BizException("明细物品名称不能为空");
            }
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("明细数量必须大于0");
            }
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            item.setUnitPrice(unitPrice);
            item.setAmount(item.getQty().multiply(unitPrice));
            total = total.add(item.getAmount());
        }
        return total;
    }

    private void saveItems(Long requestId, List<PurRequestItem> items) {
        for (PurRequestItem item : items) {
            item.setId(null);
            item.setRequestId(requestId);
            itemMapper.insert(item);
        }
    }

    private String genRequestNo() {
        String date = LocalDateTime.now().format(NO_FMT);
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return "PR-" + date + "-" + rand;
    }
}
