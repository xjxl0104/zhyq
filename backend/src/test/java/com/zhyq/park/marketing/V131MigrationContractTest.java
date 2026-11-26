package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V131 内部专属公海池与三道防撞洗数迁移契约测试：不连库静态验证 DDL 安全性、字符集与默认参数。
 */
class V131MigrationContractTest {

    private static final String V131 = "/db/migration/V147__marketing_public_sea_and_quote_calc.sql";

    @Test
    void v131CreatesIndependentSeaTablesWithoutAlteringCustomerTable() throws Exception {
        String sql = read(V131);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS crm_public_sea_meta"), "缺少公海扩展表 crm_public_sea_meta");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS crm_public_sea_wash_batch"), "缺少洗数审计表 crm_public_sea_wash_batch");
        assertFalse(sql.toUpperCase().contains("ALTER TABLE CRM_CUSTOMER"), "禁止直接 ALTER 存量大表 crm_customer 制造锁表风险");
        assertTrue(sql.contains("COLLATE=utf8mb4_general_ci"), "字符集排序规则必须对齐现有营销表 utf8mb4_general_ci");
    }

    @Test
    void v131SeedsApproved72hAndWarehouseQuotaSettingsAndRbac() throws Exception {
        String sql = read(V131);
        for (String token : List.of(
                "'sea_sla_hours'", "'72'",
                "'sea_wh_daily_cap'", "'5'",
                "'sea_wh_active_cap'", "'10'",
                "/crm/marketing/public-sea",
                "crm:marketing:public-sea:query",
                "crm:marketing:public-sea:edit",
                "'mkt_sales'", "'mkt_ops'")) {
            assertTrue(sql.contains(token), "V131 缺少关键预置配置或权限点: " + token);
        }
    }

    private static String read(String resourcePath) throws Exception {
        try (InputStream in = V131MigrationContractTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "迁移资源不存在: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
