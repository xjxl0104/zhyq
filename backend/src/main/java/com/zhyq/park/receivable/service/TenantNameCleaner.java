package com.zhyq.park.receivable.service;

/**
 * 租户名清洗:剥去行尾的半角括号注解后缀(如 {@code (新)}、{@code (新签订合同)}、
 * {@code (新)还未签合同}),即从行尾第一个半角 {@code (} 起到末尾整体去除;
 * 仅半角 {@code ( )},不动全角 {@code （ ）}(法定名里的全角括号必须保留)。
 * 前后空白 trim。用作租户去重键。
 */
public final class TenantNameCleaner {

    private TenantNameCleaner() {}

    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        // 从第一个半角左括号起到末尾整体剥离(含括号后残留说明文字);全角括号不受影响。
        return raw.trim().replaceAll("\\(.*$", "").trim();
    }
}
