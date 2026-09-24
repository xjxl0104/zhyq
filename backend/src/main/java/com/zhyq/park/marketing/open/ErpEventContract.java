package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhyq.park.common.exception.BizException;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;

/** Strict, versioned validation shared by single and batch ERP ingress. */
public final class ErpEventContract {
    private ErpEventContract() {}

    public record Event(String eventId, String event, String orderNo, String warehouseCode,
                        String customerCode, LocalDateTime occurredAt, int qty, int packages) {}

    public static Event parseAndValidate(JsonNode n) {
        if (n == null || !n.isObject()) throw new BizException("BAD_PAYLOAD:事件必须是 JSON 对象");
        String eventId = text(n, "event_id");
        String event = text(n, "event");
        String orderNo = text(n, "order_no");
        String wh = text(n, "warehouse_code");
        String customer = text(n, "customer_code");
        String occurred = text(n, "occurred_at");
        if (eventId == null) eventId = event + ":" + orderNo + ":" + occurred;
        if (event == null || orderNo == null || wh == null || customer == null || occurred == null)
            throw new BizException("BAD_PAYLOAD: event、order_no、occurred_at、warehouse_code、customer_code 必填");
        if (!java.util.Set.of("order.created", "order.paid", "order.shipped", "order.returned", "order.refunded", "order.cancelled").contains(event))
            throw new BizException("BAD_PAYLOAD:未知事件 " + event);
        LocalDateTime time = parseTime(occurred);
        int qty = n.path("qty").asInt(event.equals("order.shipped") ? 0 : 1);
        int packages = n.path("packages").asInt(event.equals("order.shipped") ? 0 : 1);
        if (event.equals("order.shipped") && (qty <= 0 || packages <= 0))
            throw new BizException("BAD_PAYLOAD: shipped 的 qty/packages 必须为正数");
        if (event.equals("order.shipped") && (!n.path("logistics").hasNonNull("tracking_no") || text(n.path("logistics"), "tracking_no") == null))
            throw new BizException("BAD_PAYLOAD: shipped 事件必须带 logistics.tracking_no");
        return new Event(eventId, event, orderNo, wh, customer, time, qty, packages);
    }

    private static String text(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) && !n.get(field).asText().isBlank() ? n.get(field).asText().trim() : null;
    }
    private static LocalDateTime parseTime(String value) {
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (Exception e) { try { return LocalDateTime.parse(value.replace(' ', 'T')); } catch (Exception bad) { throw new BizException("BAD_PAYLOAD: occurred_at 格式错误"); } }
    }
}
