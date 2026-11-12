package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import java.math.BigDecimal;
import java.util.*;

/** Shared validation for authenticated, admin and public customer intake. */
public final class MktReferralRequirements {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PLATFORMS = Set.of("天猫", "京东", "拼多多", "有赞", "抖音", "视频号", "其他");
    private static final Set<String> COURIERS = Set.of("圆通", "中通", "顺丰", "申通", "韵达", "极兔", "京东", "邮政", "其他");
    private static final Set<String> WAREHOUSE_MODES = Set.of("自建仓", "外包云仓", "两者都有");
    private static final Set<String> PAIN_POINTS = Set.of("时效慢", "错发漏发多", "大促爆仓", "成本太高", "系统对接难", "其他");
    private static final Set<String> WEIGHTS = Set.of("0-0.3", "0.3-0.5", "0.5-1", "1-2", "2-3", ">=3");
    private static final Set<String> AREA = Set.of("0-50", "50-200", "200-1000", "1000-3000", "3000-5000", ">=5000");
    private static final Set<String> COUNTS = Set.of("0-10", "10-20", "20-50", "50-100", "100-500", ">=500");
    private static final Set<String> ORDERS = Set.of("0-200", "200-1000", "1000-3000", "3000-5000", "5000-10000", ">=10000");
    private MktReferralRequirements() {}

    public static String normalize(Object input) {
        if (input == null) return null;
        if (!(input instanceof Map<?, ?> fields) || fields.size() > 30) throw new BizException("仓配需求格式不正确");
        Map<String,Object> out = new LinkedHashMap<>();
        List<String> platforms = choices(fields.get("salesPlatforms"), PLATFORMS, "销售平台");
        List<String> couriers = choices(fields.get("couriers"), COURIERS, "指定快递承运商");
        out.put("contactPosition", text(fields.get("contactPosition"), 50, "对接人职位"));
        out.put("salesPlatforms", platforms);
        out.put("salesPlatformOther", other(fields.get("salesPlatformOther"), platforms, "其他销售平台"));
        out.put("productWeights", weights(fields.get("productWeights")));
        out.put("storageArea", range(fields.get("storageArea"), AREA, "仓储面积"));
        out.put("productCount", range(fields.get("productCount"), COUNTS, "销售产品种类数"));
        out.put("skuCount", range(fields.get("skuCount"), COUNTS, "SKU 数量"));
        out.put("dailyOrders", range(fields.get("dailyOrders"), ORDERS, "日均单量"));
        out.put("itemsPerParcel", itemsPerParcel(fields.get("itemsPerParcel")));
        out.put("packaging", text(fields.get("packaging"), 500, "打包方式"));
        out.put("couriers", couriers);
        out.put("courierOther", other(fields.get("courierOther"), couriers, "其他快递承运商"));
        Object invoice = fields.get("invoiceRequired");
        if (invoice != null && !(invoice instanceof Boolean)) throw new BizException("请选择是否开发票");
        out.put("invoiceRequired", invoice);
        // 现有仓配现状
        String mode = text(fields.get("warehouseMode"), 20, "目前仓配方式");
        if (mode != null && !WAREHOUSE_MODES.contains(mode)) throw new BizException("目前仓配方式选项不正确");
        out.put("warehouseMode", mode);
        List<String> current = choices(fields.get("currentCouriers"), COURIERS, "目前合作快递");
        out.put("currentCouriers", current);
        out.put("currentCourierOther", other(fields.get("currentCourierOther"), current, "目前合作的其他快递"));
        List<String> pains = choices(fields.get("painPoints"), PAIN_POINTS, "最头疼的问题");
        out.put("painPoints", pains);
        out.put("painPointOther", other(fields.get("painPointOther"), pains, "其他头疼的问题"));
        try { return JSON.writeValueAsString(out); }
        catch (Exception e) { throw new BizException("仓配需求格式不正确"); }
    }

    public static Map<String,Object> parse(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return JSON.readValue(value, new TypeReference<Map<String,Object>>() {}); }
        catch (Exception e) { throw new BizException("客户需求数据无法读取，请联系运营处理"); }
    }

    public static String text(Object input, int max, String label) {
        if (input == null) return null;
        if (!(input instanceof String value)) throw new BizException(label + "格式不正确");
        String result = value.trim();
        if (result.length() > max) throw new BizException(label + "最多 " + max + " 字");
        return result.isEmpty() ? null : result;
    }
    private static List<String> choices(Object input, Set<String> valid, String label) {
        if (input == null) return List.of();
        if (!(input instanceof List<?> list) || list.size() > valid.size()) throw new BizException(label + "选项不正确");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String str) || !valid.contains(str) || !result.add(str)) throw new BizException(label + "选项不正确");
        }
        return List.copyOf(result);
    }
    private static String other(Object value, List<String> selected, String label) {
        String result = text(value, 100, label);
        if (!selected.contains("其他")) return null;
        if (result == null) throw new BizException("请填写" + label);
        return result;
    }
    private static String range(Object value, Set<String> valid, String label) {
        String result = text(value, 40, label);
        if (result != null && !valid.contains(result)) throw new BizException(label + "选项不正确");
        return result;
    }
    private static BigDecimal itemsPerParcel(Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) return null;
        BigDecimal result;
        try { result = new BigDecimal(value.toString().trim()); }
        catch (NumberFormatException e) { throw new BizException("平均包裹产品数量须为数字"); }
        if (result.signum() <= 0 || result.compareTo(new BigDecimal("9999")) > 0 || result.stripTrailingZeros().scale() > 2)
            throw new BizException("平均包裹产品数量应大于 0，最多两位小数");
        return result;
    }
    private static List<Map<String,Object>> weights(Object input) {
        if (input == null) return List.of();
        if (!(input instanceof List<?> list) || list.size() > WEIGHTS.size()) throw new BizException("产品重量格式不正确");
        List<Map<String,Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        int percentages = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?,?> entry)) throw new BizException("产品重量格式不正确");
            String key = range(entry.get("range"), WEIGHTS, "产品重量");
            if (key == null || !seen.add(key)) throw new BizException("请选择不同的产品重量段");
            BigDecimal percent = null;
            Object value = entry.get("percentage");
            if (value != null) {
                if (!(value instanceof Number)) throw new BizException("重量占比须为数字");
                try { percent = new BigDecimal(value.toString()); }
                catch (NumberFormatException e) { throw new BizException("重量占比须为数字"); }
                if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("100")) > 0 || percent.scale() > 2)
                    throw new BizException("重量占比应为 0–100，最多两位小数");
                total = total.add(percent); percentages++;
            }
            Map<String,Object> row = new LinkedHashMap<>(); row.put("range", key); row.put("percentage", percent); result.add(row);
        }
        if (percentages > 0 && (percentages != result.size() || total.compareTo(new BigDecimal("100")) != 0))
            throw new BizException("填写重量占比时，请填写所有已选重量段且合计为 100%");
        return result;
    }
}
