package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V57 全民营销基础表迁移的契约测试:读 SQL 文本断言表、唯一键、老表加列与种子都在。
 * 与 {@code crm/V52MigrationContractTest} 同风格,不连库。
 */
class V57MigrationContractTest {

    private static final String V57 = "/db/migration/V57__marketing_base.sql";
    private static final String V58 = "/db/migration/V58__marketing_perms_roles.sql";

    @Test
    void v57CreatesEveryStageATable() throws Exception {
        String sql = read(V57);
        for (String table : List.of(
                "sys_audit",
                "crm_promoter", "crm_promoter_account", "crm_position", "crm_position_override", "crm_position_history",
                "crm_customer_grade", "crm_commission_rule", "crm_referral_order", "crm_promoter_commission",
                "crm_settle_batch", "crm_withdrawal", "crm_warehouse", "crm_warehouse_onboarding",
                "crm_customer_lock", "crm_contract_warehouse", "crm_service_contract",
                "crm_service_contract_version", "crm_contract_template")) {
            assertTrue(sql.contains("CREATE TABLE " + table + " ("), "缺表: " + table);
        }
    }

    @Test
    void v57DeclaresIdempotencyKeys() throws Exception {
        String sql = read(V57);
        for (String uk : List.of(
                "uk_promoter_openid", "uk_promoter_phone", "uk_promoter_invite",
                "uk_position_code", "uk_position_override",
                "uk_grade_code",
                "uk_referral_order_source",
                "uk_commission_order_payee",
                "uk_settle_batch_no",
                "uk_withdrawal_pay_no",
                "uk_warehouse_code", "uk_onboarding_step",
                "uk_lock_active",
                "uk_contract_warehouse",
                "uk_service_contract_no", "uk_service_contract_version",
                "uk_contract_template")) {
            assertTrue(sql.contains(uk), "缺唯一键: " + uk);
        }
        // 一个客户同一时刻只能有一个有效锁(预锁/有效锁定),靠生成列 + 唯一键兜底并发
        assertTrue(sql.contains("active_key"), "crm_customer_lock 缺 active_key 生成列");
        // 扣回是负向新行,与正向行靠 sign 区分
        assertTrue(sql.contains("sign"), "crm_promoter_commission 缺 sign 列");
    }

    @Test
    void v57ExtendsLegacyTables() throws Exception {
        String sql = read(V57);
        assertTrue(sql.contains("ALTER TABLE crm_lead"), "未给 crm_lead 加列");
        assertTrue(sql.contains("referral_code"), "crm_lead 缺 referral_code");
        assertTrue(sql.contains("ALTER TABLE crm_customer"), "未给 crm_customer 加列");
        for (String col : List.of("grade", "referrer_id", "service_type", "biz_line", "sign_mode", "attribution_note")) {
            assertTrue(sql.contains(col), "crm_customer 缺列: " + col);
        }
        assertTrue(sql.contains("uk_customer_phone_active"), "crm_customer.phone 缺唯一约束");
        assertTrue(sql.contains("ALTER TABLE biz_contract"), "未给 biz_contract 加 grade");
    }

    @Test
    void v57SeedsPositionsGradesAndSettings() throws Exception {
        String sql = read(V57);
        for (String code : List.of("'P1'", "'P2'", "'P3'", "'P4'")) {
            assertTrue(sql.contains(code), "岗位种子缺 " + code);
        }
        for (String code : List.of("'A'", "'B'", "'C'", "'D'")) {
            assertTrue(sql.contains(code), "评级种子缺 " + code);
        }
        for (String key : List.of(
                "ladder_depth", "override_enabled", "freeze_days", "min_withdraw", "daily_referral_cap",
                "attribution_rule", "clawback_days", "old_channel_exclusive", "internal_commission",
                "tax_mode", "tax_rate", "default_sign_mode", "prelock_days", "lock_days",
                "lock_extend_days", "lock_cooldown_days", "lease_clawback_days", "lease_renew_ratio")) {
            assertTrue(sql.contains("'" + key + "'"), "规则参数种子缺 " + key);
        }
    }

    @Test
    void v58SeedsPermsRolesAndRouteMapping() throws Exception {
        String sql = read(V58);
        for (String perm : List.of(
                "crm:marketing:dashboard:query",
                "crm:marketing:promoter:query", "crm:marketing:promoter:edit", "crm:marketing:promoter:audit",
                "crm:marketing:position:query", "crm:marketing:position:config",
                "crm:marketing:grade:query", "crm:marketing:grade:config",
                "crm:marketing:customer:query", "crm:marketing:customer:edit",
                "crm:marketing:contract:query", "crm:marketing:contract:edit", "crm:marketing:contract:audit",
                "crm:marketing:template:query", "crm:marketing:template:config",
                "crm:marketing:warehouse:query", "crm:marketing:warehouse:edit", "crm:marketing:warehouse:audit",
                "crm:marketing:onboarding:query", "crm:marketing:onboarding:audit",
                "crm:marketing:order:query", "crm:marketing:order:edit",
                "crm:marketing:commission:query", "crm:marketing:commission:edit", "crm:marketing:commission:pay",
                "crm:marketing:withdrawal:query", "crm:marketing:withdrawal:audit", "crm:marketing:withdrawal:pay",
                "crm:marketing:setting:query", "crm:marketing:setting:config",
                "crm:marketing:audit:query")) {
            assertTrue(sql.contains("'" + perm + "'"), "缺权限点: " + perm);
        }
        for (String role : List.of("'mkt_sales'", "'mkt_ops'", "'mkt_finance'")) {
            assertTrue(sql.contains(role), "缺角色: " + role);
        }
        assertTrue(sql.contains("'/crm/marketing/**'"), "缺 route_module_mapping");
    }

    private static String read(String path) throws Exception {
        try (InputStream in = V57MigrationContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "迁移文件不存在: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
