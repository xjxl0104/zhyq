package com.zhyq.park.crm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V52MigrationContractTest {

    @Test
    void migrationAddsEveryRegistryColumn() throws Exception {
        String sql = read("/db/migration/V52__crm_lead_registry.sql");
        // 登记表 22 列里需要新建的字段,漏一个页面就少一栏
        for (String col : List.of(
                "lead_no", "register_date", "customer_type", "coop_mode", "goods_type",
                "order_volume", "coop_period", "budget_price", "intent_park", "region",
                "grade", "owner_name", "last_follow_date", "follow_count")) {
            assertTrue(sql.contains(col), "crm_lead 缺列: " + col);
        }
        for (String col : List.of("follow_no", "follow_date", "intent_change", "issue", "result")) {
            assertTrue(sql.contains(col), "crm_follow 缺列: " + col);
        }
    }

    /**
     * 状态由旧 8 态改为登记表 6 态。5/6 语义一致故编码不变,其余必须显式映射 ——
     * 漏掉映射会让老线索停在 7/8 这类新枚举里不存在的值上,页面显示空白且筛选不到。
     */
    @Test
    void migrationMapsLegacyStatusesIntoSixStateModel() throws Exception {
        String sql = read("/db/migration/V52__crm_lead_registry.sql");
        assertTrue(sql.contains("UPDATE crm_lead SET status = 1 WHERE status IN (2, 7)"), "待分配/公海 未映射");
        assertTrue(sql.contains("UPDATE crm_lead SET status = 2 WHERE status IN (4, 8)"), "意向/审核中 未映射");
    }

    @Test
    void migrationBackfillsNumbersAndFollowStats() throws Exception {
        String sql = read("/db/migration/V52__crm_lead_registry.sql");
        assertTrue(sql.contains("'KH-'"), "未给老线索补客户编号");
        assertTrue(sql.contains("'GF-'"), "未给老跟进记录补编号");
        assertTrue(sql.contains("uk_lead_no"), "客户编号缺唯一键");
        assertTrue(sql.contains("uk_follow_no"), "跟进编号缺唯一键");
        assertTrue(sql.contains("SET l.follow_count = f.c"), "未回填历史跟进统计");
    }

    /** 权限点必须与 LeadController / FollowController 的 @PreAuthorize 一一对应 */
    @Test
    void migrationSeedsOnePermPerEndpoint() throws Exception {
        String sql = read("/db/migration/V52__crm_lead_registry.sql");
        for (String perm : List.of(
                "crm:lead:query", "crm:lead:add", "crm:lead:edit", "crm:lead:delete",
                "crm:lead:import", "crm:follow:query", "crm:follow:add")) {
            assertTrue(sql.contains("'" + perm + "'"), "缺权限点: " + perm);
        }
        assertTrue(sql.contains("INSERT INTO sys_role_menu"), "未授权给 admin 角色");
    }

    private String read(String resource) throws Exception {
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
