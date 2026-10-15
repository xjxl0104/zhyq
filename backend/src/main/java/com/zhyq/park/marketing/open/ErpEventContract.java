package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhyq.park.common.exception.BizException;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

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
        if (eventId.length() > 128 || orderNo.length() > 64 || wh.length() > 64 || customer.length() > 64)
            throw new BizException("BAD_PAYLOAD: event_id 最多128字，订单/云仓/货主编码最多64字");
        if (!java.util.Set.of("order.created", "order.paid", "order.shipped", "order.returned", "order.refunded", "order.cancelled").contains(event))
            throw new BizException("BAD_PAYLOAD:未知事件 " + event);
        LocalDateTime time = parseTime(occurred);
        if (time.isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new BizException("BAD_PAYLOAD: occurred_at 不能晚于当前时间");
        int qty = integer(n, "qty", event.equals("order.shipped") ? 0 : 1);
        int packages = integer(n, "packages", event.equals("order.shipped") ? 0 : 1);
        if (event.equals("order.shipped") && (qty <= 0 || packages <= 0))
            throw new BizException("BAD_PAYLOAD: shipped 的 qty/packages 必须为正数");
        if (event.equals("order.shipped") && (!n.path("logistics").hasNonNull("tracking_no") || text(n.path("logistics"), "tracking_no") == null))
            throw new BizException("BAD_PAYLOAD: shipped 事件必须带 logistics.tracking_no");
        String tracking = text(n.path("logistics"), "tracking_no");
        if (tracking != null && tracking.length() > 64)
            throw new BizException("BAD_PAYLOAD: logistics.tracking_no 最多64字");
        if (n.hasNonNull("goods_amount")) {
            JsonNode money = n.get("goods_amount");
            if (!money.isNumber() || money.decimalValue().signum() < 0
                    || money.decimalValue().stripTrailingZeros().scale() > 2
                    || money.decimalValue().compareTo(new BigDecimal("999999999999.99")) > 0)
                throw new BizException("BAD_PAYLOAD: goods_amount 必须为非负金额且最多两位小数");
        }
        return new Event(eventId, event, orderNo, wh, customer, time, qty, packages);
    }

    private static String text(JsonNode n, String field) {
        if (n != null && n.hasNonNull(field) && !n.get(field).isTextual())
            throw new BizException("BAD_PAYLOAD: " + field + " 必须是字符串");
        return n != null && n.hasNonNull(field) && !n.get(field).asText().isBlank() ? n.get(field).asText().trim() : null;
    }
    private static int integer(JsonNode n, String field, int fallback) {
        if (!n.hasNonNull(field)) return fallback;
        if (!n.get(field).isIntegralNumber() || !n.get(field).canConvertToInt())
            throw new BizException("BAD_PAYLOAD: " + field + " 必须为有效整数");
        return n.get(field).intValue();
    }
    private static LocalDateTime parseTime(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime(); }
        catch (Exception e) { try { return LocalDateTime.parse(value.replace(' ', 'T')); } catch (Exception bad) { throw new BizException("BAD_PAYLOAD: occurred_at 格式错误"); } }
    }
}
