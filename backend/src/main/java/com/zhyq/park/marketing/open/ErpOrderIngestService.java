package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktOutboundOrderStateService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService.OutboundRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Authenticated ERP events and manual imports share a warehouse-serialized ledger transaction. */
@Service
@RequiredArgsConstructor
public class ErpOrderIngestService {
    private static final ObjectMapper PAYLOAD_JSON = new ObjectMapper();
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseErpMapper erpMapper;
    private final MktCustomerErpMapMapper mapMapper;
    private final MktErpSyncLogMapper logMapper;
    private final MktErpEventInboxMapper inboxMapper;
    private final MktErpUnmappedMapper unmappedMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktReferralOrderImportService importService;
    private final MktCommissionService commissionService;
    private final MktOutboundOrderStateService orderStateService;

    public record IngestResult(boolean ok, Long orderId, boolean attributed, String code, String msg, boolean auditAfterRollback) {
        public IngestResult(boolean ok, Long orderId, boolean attributed, String code, String msg) {
            this(ok, orderId, attributed, code, msg, false);
        }
        static IngestResult ok(Long id, boolean attributed) { return new IngestResult(true, id, attributed, null, null); }
        static IngestResult fail(String code, String msg) { return new IngestResult(false, null, false, code, msg); }
        public boolean sandbox() { return "SANDBOX_VALIDATED".equals(code); }
        public String status() { return !ok ? "REJECTED" : code == null ? "PROCESSED" : code; }
    }

    @Transactional
    public IngestResult ingest(MktWarehouseErp cred, JsonNode ev, String digest) {
        MktErpSyncLog logRow = newLog(cred, text(ev, "event"), text(ev, "order_no"), digest);
        boolean sandbox = Integer.valueOf(1).equals(cred.getEnv());
        try {
            ErpEventContract.Event contract = ErpEventContract.parseAndValidate(ev);
            MktWarehouse wh = warehouseMapper.selectForUpdate(cred.getWarehouseId());
            MktWarehouseErp current = erpMapper.selectById(cred.getId());
            if (current == null || !Integer.valueOf(1).equals(current.getStatus())
                    || !Objects.equals(current.getWarehouseId(), cred.getWarehouseId())
                    || !Objects.equals(current.getAppId(), cred.getAppId())
                    || !Objects.equals(current.getEnv(), cred.getEnv())
                    || !Objects.equals(current.getSecretEnc(), cred.getSecretEnc())
                    || !Objects.equals(current.getConnectionRevision(), cred.getConnectionRevision()))
                return reject(logRow, sandbox, "CREDENTIAL_CHANGED", "凭证配置已变化，请使用当前配置重新签名");
            if (wh == null || !contract.warehouseCode().equals(wh.getCode()))
                return reject(logRow, sandbox, "WH_MISMATCH", "warehouse_code 与 App-Id 对应的云仓不一致");
            if (Integer.valueOf(8).equals(wh.getJoinStatus()))
                return reject(logRow, sandbox, "WH_FROZEN", "云仓冻结待删除，暂不接收订单事件");
            if (sandbox) return new IngestResult(true, null, false, "SANDBOX_VALIDATED",
                    "沙箱事件字段校验通过；未写入正式订单、收件箱或佣金");
            if (!Integer.valueOf(2).equals(current.getEnv()))
                return reject(logRow, false, "BAD_ENV", "凭证环境无效");
            MktErpEventInbox inbox = inboxMapper.selectOne(new LambdaQueryWrapper<MktErpEventInbox>()
                    .eq(MktErpEventInbox::getWarehouseId, cred.getWarehouseId()).eq(MktErpEventInbox::getAppId, cred.getAppId())
                    .eq(MktErpEventInbox::getEventId, contract.eventId()).last("limit 1 FOR UPDATE"));
            // Identity cannot change when waiting or retrying, including legacy UNMAPPED rows.
            if (inbox != null && !samePayload(inbox, ev, digest))
                return reject(logRow, false, "EVENT_CONFLICT", "相同 event_id 不允许更换事件内容");
            boolean legacyUnmapped = inbox != null && "order.shipped".equals(inbox.getEvent()) && "UNMAPPED".equals(inbox.getLastError());
            if (inbox != null && "PROCESSED".equals(inbox.getStatus()) && !legacyUnmapped) {
                Long existing = existingId(wh.getId(), contract.orderNo());
                return IngestResult.ok(existing, existing != null);
            }
            if (inbox == null) {
                inbox = new MktErpEventInbox();
                inbox.setWarehouseId(wh.getId()); inbox.setAppId(cred.getAppId()); inbox.setEventId(contract.eventId());
                inbox.setEvent(contract.event()); inbox.setOrderNo(contract.orderNo()); inbox.setPayloadDigest(digest);
                inbox.setPayloadJson(ev.toString()); inbox.setOccurredAt(contract.occurredAt());
                inbox.setStatus("PROCESSING"); inbox.setAttempts(1); inbox.setProjectId(cred.getProjectId());
                inboxMapper.insert(inbox);
            }
            IngestResult result = switch (contract.event()) {
                case "order.shipped" -> shipped(wh, contract, ev);
                case "order.refunded", "order.cancelled" -> terminate(cred, contract);
                default -> {
                    Long id = existingId(wh.getId(), contract.orderNo());
                    yield IngestResult.ok(id, id != null);
                }
            };
            boolean waiting = "WAITING_MAPPING".equals(result.code());
            if (waiting) recordUnmapped(cred, contract, ev, inbox);
            else closeUnmapped(inbox.getId(), result.orderId());
            // Explicit null assignments clear stale UNMAPPED flags: updateById ignores null fields.
            inboxMapper.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                    .eq(MktErpEventInbox::getId, inbox.getId())
                    .set(MktErpEventInbox::getStatus, waiting ? "WAITING_MAPPING" : "PROCESSED")
                    .set(MktErpEventInbox::getProcessedAt, waiting ? null : LocalDateTime.now())
                    .set(MktErpEventInbox::getNextRetryAt, null).set(MktErpEventInbox::getLastError, waiting ? "UNMAPPED" : null));
            logRow.setHttpStatus(200); logRow.setOk(1); logRow.setReferralOrderId(result.orderId());
            logRow.setErrorCode(waiting ? "UNMAPPED" : null); logRow.setError(waiting ? result.msg() : null);
            logMapper.insert(logRow);
            if (!waiting) touch(cred);
            return result;
        } catch (BizException e) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            String message = e.getMessage() == null ? "业务校验失败" : e.getMessage();
            String code = message.startsWith("BAD_PAYLOAD") ? "BAD_PAYLOAD" : message.startsWith("ORDER_CONFLICT") ? "ORDER_CONFLICT" : "BIZ";
            // The surrounding transaction is rolling back; the caller records this failure after it returns.
            return new IngestResult(false, null, false, code, message, !sandbox);
        }
    }

    private IngestResult shipped(MktWarehouse wh, ErpEventContract.Event event, JsonNode ev) {
        if (orderStateService.isTerminal(wh.getId(), event.orderNo()))
            return new IngestResult(true, existingId(wh.getId(), event.orderNo()), false,
                    "IGNORED_TERMINAL", "该出库单已取消或退款，后到发货事件不入账");
        MktCustomerErpMap map = mapMapper.selectOne(new LambdaQueryWrapper<MktCustomerErpMap>()
                .eq(MktCustomerErpMap::getWarehouseId, wh.getId()).eq(MktCustomerErpMap::getCustomerCode, event.customerCode())
                .eq(MktCustomerErpMap::getStatus, 1).last("limit 1"));
        if (map == null) return new IngestResult(true, null, false, "WAITING_MAPPING", "货主尚未匹配客户；补齐映射后按原事件补处理");
        BigDecimal goods = ev.hasNonNull("goods_amount") ? ev.get("goods_amount").decimalValue() : null;
        OutboundRow row = new OutboundRow(event.orderNo(), null, event.qty(), event.packages(), event.occurredAt(),
                text(ev.path("logistics"), "tracking_no"), wh.getCode(), goods);
        var result = importService.ingestOutbound(row, wh.getProjectId(), map.getCustomerId());
        if (result.terminal()) return new IngestResult(true, result.order() == null ? null : result.order().getId(),
                false, "IGNORED_TERMINAL", "该出库单已取消或退款，不重复入账");
        MktReferralOrder order = result.order();
        if (result.created()) orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                    .eq(MktReferralOrder::getId, order.getId()).eq(MktReferralOrder::getWarehouseId, wh.getId())
                    .set(MktReferralOrder::getCustomerCode, event.customerCode())
                    .set(MktReferralOrder::getRawPayload, ev.toString().length() > 4000 ? null : ev.toString()));
        return IngestResult.ok(order.getId(), true);
    }

    private IngestResult terminate(MktWarehouseErp cred, ErpEventContract.Event event) {
        orderStateService.terminate(cred.getWarehouseId(), event.orderNo(), event.event(), cred.getAppId(),
                event.eventId(), event.occurredAt(), cred.getProjectId());
        MktReferralOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getWarehouseId, cred.getWarehouseId()).eq(MktReferralOrder::getSourceNo, event.orderNo())
                .last("limit 1 FOR UPDATE"));
        if (order != null) {
            boolean refund = "order.refunded".equals(event.event());
            String reason = refund ? "ERP 退款" : "ERP 取消";
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                    .eq(MktReferralOrder::getId, order.getId()).eq(MktReferralOrder::getWarehouseId, cred.getWarehouseId())
                    .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                    .set(MktReferralOrder::getStatus, refund ? MktCommissionService.ORDER_REFUNDED : MktCommissionService.ORDER_CANCELLED)
                    .set(MktReferralOrder::getRemark, reason));
            if (updated == 1) commissionService.clawback(order.getId(), reason);
        }
        // A terminal event also resolves pending shipments that still have no customer mapping.
        unmappedMapper.update(null, new LambdaUpdateWrapper<MktErpUnmapped>()
                .eq(MktErpUnmapped::getWarehouseId, cred.getWarehouseId()).eq(MktErpUnmapped::getOrderNo, event.orderNo())
                .eq(MktErpUnmapped::getStatus, "OPEN").set(MktErpUnmapped::getStatus, "IGNORED")
                .set(MktErpUnmapped::getReplayedAt, LocalDateTime.now()));
        inboxMapper.update(null, new LambdaUpdateWrapper<MktErpEventInbox>()
                .eq(MktErpEventInbox::getWarehouseId, cred.getWarehouseId()).eq(MktErpEventInbox::getOrderNo, event.orderNo())
                .eq(MktErpEventInbox::getEvent, "order.shipped").eq(MktErpEventInbox::getStatus, "WAITING_MAPPING")
                .set(MktErpEventInbox::getStatus, "PROCESSED").set(MktErpEventInbox::getLastError, null)
                .set(MktErpEventInbox::getNextRetryAt, null).set(MktErpEventInbox::getProcessedAt, LocalDateTime.now()));
        return IngestResult.ok(order == null ? null : order.getId(), order != null);
    }

    private void recordUnmapped(MktWarehouseErp cred, ErpEventContract.Event event, JsonNode ev, MktErpEventInbox inbox) {
        MktErpUnmapped existing = unmappedMapper.selectOne(new LambdaQueryWrapper<MktErpUnmapped>()
                .eq(MktErpUnmapped::getInboxId, inbox.getId()).last("limit 1"));
        if (existing != null) return;
        MktErpUnmapped item = new MktErpUnmapped();
        item.setWarehouseId(cred.getWarehouseId()); item.setCustomerCode(event.customerCode()); item.setOrderNo(event.orderNo());
        item.setEventId(event.eventId()); item.setInboxId(inbox.getId()); item.setStatus("OPEN");
        item.setPayloadJson(ev.toString()); item.setProjectId(cred.getProjectId()); unmappedMapper.insert(item);
    }

    private void closeUnmapped(Long inboxId, Long orderId) {
        MktReferralOrder order = orderId == null ? null : orderMapper.selectById(orderId);
        unmappedMapper.update(null, new LambdaUpdateWrapper<MktErpUnmapped>()
                .eq(MktErpUnmapped::getInboxId, inboxId).eq(MktErpUnmapped::getStatus, "OPEN")
                .set(MktErpUnmapped::getStatus, "RESOLVED").set(MktErpUnmapped::getReplayedAt, LocalDateTime.now())
                .set(MktErpUnmapped::getMappedCustomerId, order == null ? null : order.getCustomerId()));
    }

    private Long existingId(Long warehouseId, String orderNo) {
        MktReferralOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getSourceNo, orderNo).eq(MktReferralOrder::getWarehouseId, warehouseId)
                .select(MktReferralOrder::getId).last("limit 1 FOR UPDATE"));
        return order == null ? null : order.getId();
    }

    private void touch(MktWarehouseErp cred) {
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getId, cred.getId()).set(MktWarehouseErp::getLastSyncAt, LocalDateTime.now()));
    }

    private IngestResult reject(MktErpSyncLog row, boolean sandbox, String code, String msg) {
        if (!sandbox) {
            row.setHttpStatus(400); row.setOk(0); row.setErrorCode(clipped(code, 32)); row.setError(clipped(msg, 500)); logMapper.insert(row);
        }
        return IngestResult.fail(code, msg);
    }

    public void logRejected(String appId, String code, String msg, String digest) {
        MktErpSyncLog row = new MktErpSyncLog();
        row.setAppId(clipped(appId, 32)); row.setDirection("in"); row.setPayloadDigest(clipped(digest, 64));
        reject(row, false, code, msg);
    }

    /** Called by HTTP ingress/replay after the ledger transaction has rolled back, never from ingest itself. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRolledBackFailure(MktWarehouseErp cred, JsonNode event, String digest, IngestResult result) {
        if (result == null || result.ok() || !result.auditAfterRollback() || !Integer.valueOf(2).equals(cred.getEnv())) return;
        reject(newLog(cred, text(event, "event"), text(event, "order_no"), digest), false, result.code(), result.msg());
    }

    private static MktErpSyncLog newLog(MktWarehouseErp cred, String event, String orderNo, String digest) {
        MktErpSyncLog row = new MktErpSyncLog();
        row.setWarehouseId(cred.getWarehouseId()); row.setAppId(clipped(cred.getAppId(), 32)); row.setDirection("in");
        row.setEvent(clipped(event, 32)); row.setOrderNo(clipped(orderNo, 64)); row.setPayloadDigest(clipped(digest, 64)); row.setProjectId(cred.getProjectId());
        return row;
    }

    private static String text(JsonNode node, String key) {
        return node != null && node.hasNonNull(key) ? node.get(key).asText().trim() : null;
    }

    private static String clipped(String value, int limit) { return value == null || value.length() <= limit ? value : value.substring(0, limit); }

    private static boolean samePayload(MktErpEventInbox original, JsonNode incoming, String digest) {
        if (Objects.equals(original.getPayloadDigest(), digest)) return true;
        // A batch item and a single HTTP body may differ only in JSON whitespace or field order.
        // Keep the original digest and stored event intact; accept only equal parsed payloads.
        if (original.getPayloadJson() == null) return false;
        try { return PAYLOAD_JSON.readTree(original.getPayloadJson()).equals(incoming); }
        catch (Exception invalidOriginal) { return false; }
    }
}
