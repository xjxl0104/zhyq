package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V133 全量 18 通道热插拔连接器中心、10秒扫码验店、快递100轨迹抽检与 6% 专票验真迁移契约测试。
 */
class V133MigrationContractTest {

    private static final String V133 = "/db/migration/V149__full_ext_connector_hub_and_verifiers.sql";

    @Test
    void v133CreatesFourVerifierTablesWithoutAlteringCustomerTable() throws Exception {
        String sql = read(V133);
        assertFalse(sql.toUpperCase().contains("ALTER TABLE CRM_CUSTOMER"), "禁止 ALTER 存量核心表 crm_customer");
        for (String table : List.of(
                "CREATE TABLE IF NOT EXISTS crm_ext_connector_config",
                "CREATE TABLE IF NOT EXISTS crm_shop_oauth_session",
                "CREATE TABLE IF NOT EXISTS crm_waybill_spot_check",
                "CREATE TABLE IF NOT EXISTS crm_invoice_verify_log")) {
            assertTrue(sql.contains(table), "V133 缺少核心表声明: " + table);
        }
        assertTrue(sql.contains("COLLATE=utf8mb4_general_ci"), "字符集排序规则须统一 utf8mb4_general_ci");
    }

    @Test
    void v133SeedsAllEighteenPlugAndPlayConnectors() throws Exception {
        String sql = read(V133);
        for (String code : List.of(
                "'QCC_PRO'", "'TYC_PRO'", "'LIGHT_CORP'", "'TUNGEE_API'",
                "'CHANMAMA_API'", "'DIANTOU_API'", "'OAUTH_DOUYIN'", "'OAUTH_TAOBAO'", "'OAUTH_PDD'",
                "'AMAP_LBS'",
                "'AD_FEIYU'", "'AD_TENCENT'", "'AD_XHS'", "'AD_BAIDU'",
                "'KUAIDI100'", "'IM_WEBHOOK'", "'PRIVACY_AXB'", "'VAT_INVOICE'")) {
            assertTrue(sql.contains(code), "V133 缺少预置连接器编码: " + code);
        }
    }

    private static String read(String resourcePath) throws Exception {
        try (InputStream in = V133MigrationContractTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "迁移资源不存在: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
