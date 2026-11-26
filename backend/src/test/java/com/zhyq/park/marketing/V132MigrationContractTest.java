package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V132 广东主力分区省钱锁、30天工商缓存、1客多店矩阵、高德500m打卡与飞鱼Webhook迁移契约测试。
 */
class V132MigrationContractTest {

    private static final String V132 = "/db/migration/V148__public_sea_gd_amap_qcc_feiyu.sql";

    @Test
    void v132CreatesGdAmapQccTablesWithoutAlteringCustomerTable() throws Exception {
        String sql = read(V132);
        assertFalse(sql.toUpperCase().contains("ALTER TABLE CRM_CUSTOMER"), "禁止直接 ALTER 存量核心表 crm_customer");
        for (String table : List.of(
                "ALTER TABLE crm_public_sea_meta",
                "ALTER TABLE crm_public_sea_wash_batch",
                "CREATE TABLE IF NOT EXISTS crm_ext_company_cache",
                "CREATE TABLE IF NOT EXISTS crm_public_sea_shop",
                "CREATE TABLE IF NOT EXISTS crm_public_sea_checkin",
                "CREATE TABLE IF NOT EXISTS crm_ext_ad_lead_inbox")) {
            assertTrue(sql.contains(table), "V132 缺少表变更声明: " + table);
        }
        assertTrue(sql.contains("COLLATE=utf8mb4_general_ci"), "字符集排序规则须统一使用 utf8mb4_general_ci");
    }

    @Test
    void v132SeedsGuangdongLockRulesButNoPlaintextConnectorSecrets() throws Exception {
        String sql = read(V132);
        for (String key : List.of(
                "'sea_region_lock_province'", "'广东省'",
                "'sea_checkin_max_meters'", "'500'",
                "'sea_visit_protect_days'", "'15'")) {
            assertTrue(sql.contains(key), "V132 缺少预置配置键: " + key);
        }
        assertFalse(sql.contains("'ext_"), "外部接口密钥不得再以明文写入 biz_setting，应统一放连接器中心");
    }

    private static String read(String resourcePath) throws Exception {
        try (InputStream in = V132MigrationContractTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "迁移资源不存在: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
