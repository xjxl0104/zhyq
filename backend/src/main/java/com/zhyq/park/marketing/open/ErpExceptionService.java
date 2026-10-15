package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/** 原事件补处理：只排队，事务提交后由重放任务执行，保持事件身份与订单幂等。 */
@Service
@RequiredArgsConstructor
public class ErpExceptionService {
    private static final Set<String> RETRYABLE = Set.of("WAITING_MAPPING", "RETRY", "DEAD", "RECEIVED");
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseErpMapper credentials;
    private final MktErpEventInboxMapper inbox;
    private final MktErpUnmappedMapper unmapped;
    private final MktErpDeadLetterMapper deadLetters;
    private final MktAuditService audit;

    public record ExceptionView(Long id, String eventId, String event, String orderNo, String status,
                                Integer attempts, String lastError, LocalDateTime occurredAt,
                                LocalDateTime nextRetryAt) {}

    public PageResult<ExceptionView> page(Long warehouseId, int pageNo, int pageSize) {
        if (warehouses.selectById(warehouseId) == null) throw new BizException("云仓不存在");
        var result = inbox.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, Math.min(100, pageSize))),
                new LambdaQueryWrapper<MktErpEventInbox>().eq(MktErpEventInbox::getWarehouseId, warehouseId)
                        .and(q -> q.in(MktErpEventInbox::getStatus, RETRYABLE).or().eq(MktErpEventInbox::getStatus, "PROCESSING")
                                .or(x -> x.eq(MktErpEventInbox::getStatus, "PROCESSED").eq(MktErpEventInbox::getLastError, "UNMAPPED")
                                        .eq(MktErpEventInbox::getEvent, "order.shipped")))
                        .orderByDesc(MktErpEventInbox::getId));
        return PageResult.of(result.getTotal(), result.getRecords().stream().map(row -> new ExceptionView(
                row.getId(), row.getEventId(), row.getEvent(), row.getOrderNo(), legacyUnmapped(row) ? "WAITING_MAPPING" : row.getStatus(),
                row.getAttempts(), row.getLastError(), row.getOccurredAt(), row.getNextRetryAt())).toList());
    }

    /** 调用者保存映射的同一事务内执行；仓库锁也使它与订单入站串行。 */
    @Transactional
    public int enqueueForMapping(Long warehouseId, String customerCode) {
        requireWarehouse(warehouseId);
        MktWarehouseErp credential = current(warehouseId);
        // 允许先维护映射；未启用正式环境时保留等待项，绝不把沙箱事件补到正式账务。
        if (!formal(credential)) return 0;
        int queued = 0;
        for (MktErpUnmapped pending : unmapped.selectList(new LambdaQueryWrapper<MktErpUnmapped>()
                .eq(MktErpUnmapped::getWarehouseId, warehouseId).eq(MktErpUnmapped::getCustomerCode, customerCode)
                .eq(MktErpUnmapped::getStatus, "OPEN"))) {
            MktErpEventInbox row = pending.getInboxId() == null ? null : inbox.selectById(pending.getInboxId());
            if (row == null || !warehouseId.equals(row.getWarehouseId()) || !credential.getAppId().equals(row.getAppId())
                    || row.getPayloadJson() == null || row.getPayloadJson().isBlank()
                    || !"order.shipped".equals(row.getEvent()) || (!RETRYABLE.contains(row.getStatus()) && !legacyUnmapped(row))) continue;
            queued += enqueue(row, "货主映射已保存，等待补处理");
        }
        if (queued > 0) audit.log("erp.mapping.enqueue", "warehouse", warehouseId, "补映射后排队 " + queued + " 条原始事件");
        return queued;
    }

    @Transactional
    public void retry(Long warehouseId, Long inboxId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) throw new BizException("请填写 1–200 字的重试原因");
        requireWarehouse(warehouseId);
        MktWarehouseErp credential = current(warehouseId);
        if (!formal(credential)) throw new BizException("请先启用该云仓正式 ERP 凭证，再补处理历史事件");
        MktErpEventInbox row = inbox.selectById(inboxId);
        if (row == null || !warehouseId.equals(row.getWarehouseId()) || !credential.getAppId().equals(row.getAppId()))
            throw new BizException(404, "事件不存在或不属于该云仓凭证");
        if (!RETRYABLE.contains(row.getStatus()) && !legacyUnmapped(row)) throw new BizException("该事件已完成或正在处理，请刷新状态");
        if (row.getPayloadJson() == null || row.getPayloadJson().isBlank()) throw new BizException("缺少原始事件，请由 ERP 使用原事件标识重新推送");
        enqueue(row, "人工重试：" + reason.trim());
        audit.log("erp.exception.retry", "erp_inbox", row.getId(), reason.trim());
    }

    private int enqueue(MktErpEventInbox row, String reason) {
        if ("RECEIVED".equals(row.getStatus())) return 0;
        int changed = inbox.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getId, row.getId()).eq(MktErpEventInbox::getWarehouseId, row.getWarehouseId())
                .eq(MktErpEventInbox::getStatus, row.getStatus()).set(MktErpEventInbox::getStatus, "RECEIVED")
                .set(MktErpEventInbox::getAttempts, 0).set(MktErpEventInbox::getNextRetryAt, null)
                .set(MktErpEventInbox::getProcessedAt, null).set(MktErpEventInbox::getLastError, reason));
        if (changed == 1) deadLetters.update(null, new LambdaUpdateWrapper<MktErpDeadLetter>()
                .eq(MktErpDeadLetter::getInboxId, row.getId()).set(MktErpDeadLetter::getReplayedAt, LocalDateTime.now())
                .set(MktErpDeadLetter::getReplayedBy, MktAuditService.currentOperator()));
        return changed;
    }

    private void requireWarehouse(Long id) {
        MktWarehouse w = warehouses.selectForUpdate(id);
        if (w == null) throw new BizException("云仓不存在");
        if (Integer.valueOf(8).equals(w.getJoinStatus())) throw new BizException("云仓冻结待删除，不能补处理事件");
    }
    private MktWarehouseErp current(Long id) {
        return credentials.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, id));
    }
    private static boolean formal(MktWarehouseErp c) {
        return c != null && Integer.valueOf(1).equals(c.getStatus()) && Integer.valueOf(2).equals(c.getEnv());
    }
    private static boolean legacyUnmapped(MktErpEventInbox row) {
        return "PROCESSED".equals(row.getStatus()) && "UNMAPPED".equals(row.getLastError()) && "order.shipped".equals(row.getEvent());
    }
}
