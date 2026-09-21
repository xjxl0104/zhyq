package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.entity.MktErpSyncLog;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktErpUnmapped;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktCustomerErpMapMapper;
import com.zhyq.park.marketing.mapper.MktErpSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktErpUnmappedMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService.OutboundRow;
import com.zhyq.park.common.setting.BizSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * ERP 订单事件入账(PARK-MKT-001 §2.6):
 * <ul>
 *   <li>归属只认 customer_code → crm_customer_erp_map → 客户 → 推荐伙伴;未映射 = 云仓自有客户,记日志不计佣(attributed=false);</li>
 *   <li>order.shipped → 复用文件导入同一条路径(服务费按合同单价表园区自算)→ 计佣(冻结,freeze_days 后解冻);</li>
 *   <li>order.created / paid → 只登记;order.refunded / cancelled → 扣回或作废;order.returned → 基数为服务费时不扣;</li>
 *   <li>去重键 (source_type=2, order_no);乱序:refunded 先到则记 tombstone,shipped 到达时直接作废。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpOrderIngestService {

    private static final String MODULE = "marketing";

    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseErpMapper erpMapper;
    private final MktCustomerErpMapMapper mapMapper;
    private final MktErpSyncLogMapper logMapper;
    private final MktErpEventInboxMapper inboxMapper;
    private final MktErpUnmappedMapper unmappedMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktReferralOrderImportService importService;
    private final MktCommissionService commissionService;
    private final BizSettings bizSettings;

    public record IngestResult(boolean ok, Long orderId, boolean attributed, String code, String msg) {
        static IngestResult ok(Long orderId, boolean attributed) { return new IngestResult(true, orderId, attributed, null, null); }
        static IngestResult fail(String code, String msg) { return new IngestResult(false, null, false, code, msg); }
    }

    @Transactional
    public IngestResult ingest(MktWarehouseErp cred, JsonNode ev, String digest) {
        String event = text(ev, "event");
        String orderNo = text(ev, "order_no");
        String whCode = text(ev, "warehouse_code");
        String customerCode = text(ev, "customer_code");
        MktErpSyncLog logRow = newLog(cred, event, orderNo, digest);
        try {
            ErpEventContract.Event contract = ErpEventContract.parseAndValidate(ev);
            event = contract.event();
            orderNo = contract.orderNo();
            MktWarehouse wh = warehouseMapper.selectById(cred.getWarehouseId());
            if (wh == null || (whCode != null && !whCode.equals(wh.getCode()))) {
                return fail(logRow, 400, "WH_MISMATCH", "warehouse_code 与 App-Id 对应的云仓不一致");
            }
            MktErpEventInbox inbox = inboxMapper.selectOne(new LambdaQueryWrapper<MktErpEventInbox>()
                    .eq(MktErpEventInbox::getWarehouseId, cred.getWarehouseId())
                    .eq(MktErpEventInbox::getAppId, cred.getAppId())
                    .eq(MktErpEventInbox::getEventId, contract.eventId()).last("limit 1"));
            if (inbox != null && "PROCESSED".equals(inbox.getStatus())) {
                return IngestResult.ok(existingId(orderNo), true);
            }
            if (inbox == null) {
                inbox = new MktErpEventInbox();
                inbox.setWarehouseId(cred.getWarehouseId()); inbox.setAppId(cred.getAppId()); inbox.setEventId(contract.eventId());
                inbox.setEvent(event); inbox.setOrderNo(orderNo); inbox.setPayloadDigest(digest); inbox.setPayloadJson(ev.toString());
                inbox.setOccurredAt(contract.occurredAt()); inbox.setStatus("PROCESSING"); inbox.setAttempts(1); inbox.setProjectId(cred.getProjectId());
                inboxMapper.insert(inbox);
            }
            touch(cred);
            IngestResult r = switch (event) {
                case "order.shipped" -> shipped(wh, orderNo, customerCode, ev);
                case "order.refunded" -> clawback(orderNo, MktCommissionService.ORDER_REFUNDED, "ERP 退款");
                case "order.cancelled" -> clawback(orderNo, MktCommissionService.ORDER_CANCELLED, "ERP 取消");
                case "order.created", "order.paid", "order.returned" -> IngestResult.ok(existingId(orderNo), existingId(orderNo) != null);
                default -> IngestResult.fail("BAD_PAYLOAD", "未知事件 " + event);
            };
            if (!r.ok()) {
                return fail(logRow, 400, r.code(), r.msg());
            }
            logRow.setHttpStatus(200);
            logRow.setOk(1);
            logRow.setReferralOrderId(r.orderId());
            if (!r.attributed()) {
                logRow.setErrorCode("UNMAPPED");
                logRow.setError("customer_code 未映射,视为云仓自有客户,不计佣");
                MktErpUnmapped unmapped = new MktErpUnmapped();
                unmapped.setWarehouseId(cred.getWarehouseId()); unmapped.setCustomerCode(customerCode); unmapped.setOrderNo(orderNo);
                unmapped.setEventId(contract.eventId()); unmapped.setInboxId(inbox.getId()); unmapped.setStatus("OPEN");
                unmapped.setPayloadJson(ev.toString()); unmapped.setProjectId(cred.getProjectId());
                unmappedMapper.insert(unmapped);
            }
            inbox.setStatus("PROCESSED"); inbox.setProcessedAt(LocalDateTime.now()); inbox.setLastError(r.attributed() ? null : "UNMAPPED");
            inboxMapper.updateById(inbox);
            logMapper.insert(logRow);
            return r;
        } catch (BizException e) {
            String message = e.getMessage() == null ? "业务校验失败" : e.getMessage();
            String code = message.startsWith("BAD_PAYLOAD") ? "BAD_PAYLOAD" : "BIZ";
            return fail(logRow, 400, code, message);
        }
    }

    private IngestResult shipped(MktWarehouse wh, String orderNo, String customerCode, JsonNode ev) {
        Long existing = existingId(orderNo);
        if (existing != null) {
            return IngestResult.ok(existing, true);
        }
        if (customerCode == null) {
            return IngestResult.fail("BAD_PAYLOAD", "shipped 事件缺 customer_code");
        }
        if (text(ev.path("logistics"), "tracking_no") == null) {
            return IngestResult.fail("BAD_PAYLOAD", "shipped 事件必须带 logistics.tracking_no(§2.13 防刷单)");
        }
        MktCustomerErpMap map = mapMapper.selectOne(new LambdaQueryWrapper<MktCustomerErpMap>()
                .eq(MktCustomerErpMap::getWarehouseId, wh.getId())
                .eq(MktCustomerErpMap::getCustomerCode, customerCode)
                .eq(MktCustomerErpMap::getStatus, 1).last("limit 1"));
        if (map == null) {
            return IngestResult.ok(null, false);
        }
        String phone = importService.customerPhone(map.getCustomerId());
        int qty = ev.path("qty").asInt(0);
        int packages = ev.path("packages").asInt(1);
        LocalDateTime shippedAt = parseTime(text(ev, "occurred_at"));
        BigDecimal goods = ev.hasNonNull("goods_amount") ? ev.get("goods_amount").decimalValue() : null;
        OutboundRow row = new OutboundRow(orderNo, phone, qty, packages, shippedAt,
                text(ev.path("logistics"), "tracking_no"), wh.getCode(), goods);
        MktReferralOrder order = commissionService.createAndSplit(
                importService.toEvent(row, wh.getProjectId(), bizSettings.getInt(MODULE, "freeze_days", 7)));
        orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getId, order.getId())
                .set(MktReferralOrder::getWarehouseId, wh.getId())
                .set(MktReferralOrder::getCustomerCode, customerCode)
                .set(MktReferralOrder::getQty, qty)
                .set(MktReferralOrder::getPackages, packages)
                .set(MktReferralOrder::getGoodsAmount, goods)
                .set(MktReferralOrder::getLogisticsNo, text(ev.path("logistics"), "tracking_no"))
                .set(MktReferralOrder::getServiceFee, order.getBaseAmount())
                .set(MktReferralOrder::getRawPayload, ev.toString().length() > 4000 ? null : ev.toString()));
        return IngestResult.ok(order.getId(), true);
    }

    /** 退款/取消:订单存在则改状态并扣回;不存在则不建 tombstone(shipped 再到时按正常入账,由业务判断)。 */
    private IngestResult clawback(String orderNo, int toStatus, String reason) {
        MktReferralOrder o = orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getSourceNo, orderNo).last("limit 1"));
        if (o == null) {
            return IngestResult.ok(null, false);
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getId, o.getId())
                .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .set(MktReferralOrder::getStatus, toStatus)
                .set(MktReferralOrder::getRemark, reason));
        if (updated == 1) {
            commissionService.clawback(o.getId(), reason);
        }
        return IngestResult.ok(o.getId(), true);
    }

    private Long existingId(String orderNo) {
        MktReferralOrder o = orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getSourceNo, orderNo).select(MktReferralOrder::getId).last("limit 1"));
        return o == null ? null : o.getId();
    }

    private void touch(MktWarehouseErp cred) {
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getId, cred.getId()).set(MktWarehouseErp::getLastSyncAt, LocalDateTime.now()));
    }

    private IngestResult fail(MktErpSyncLog logRow, int http, String code, String msg) {
        logRow.setHttpStatus(http);
        logRow.setOk(0);
        logRow.setErrorCode(code);
        logRow.setError(msg);
        logMapper.insert(logRow);
        return IngestResult.fail(code, msg);
    }

    /** 验签失败也要留痕(cred 可能为空)。 */
    public void logRejected(String appId, String code, String msg, String digest) {
        MktErpSyncLog l = new MktErpSyncLog();
        l.setAppId(appId);
        l.setDirection("in");
        l.setHttpStatus(400);
        l.setOk(0);
        l.setErrorCode(code);
        l.setError(msg);
        l.setPayloadDigest(digest);
        logMapper.insert(l);
    }

    private static MktErpSyncLog newLog(MktWarehouseErp cred, String event, String orderNo, String digest) {
        MktErpSyncLog l = new MktErpSyncLog();
        l.setWarehouseId(cred.getWarehouseId());
        l.setAppId(cred.getAppId());
        l.setDirection("in");
        l.setEvent(event);
        l.setOrderNo(orderNo);
        l.setPayloadDigest(digest);
        l.setProjectId(cred.getProjectId());
        return l;
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) && !n.get(field).asText().isBlank() ? n.get(field).asText() : null;
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception e) {
            try { return LocalDateTime.parse(s.replace(' ', 'T')); } catch (Exception e2) { return LocalDateTime.now(); }
        }
    }
}
