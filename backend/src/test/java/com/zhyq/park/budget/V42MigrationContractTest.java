package com.zhyq.park.budget;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42MigrationContractTest {

    @Test
    void migrationCreatesBudgetTableFlowAndMenus() throws Exception {
        String sql = read("/db/migration/V42__budget_management.sql");
        for (String token : List.of(
                "CREATE TABLE bud_budget",
                "budget_type",
                "uk_budget_no",
                // 审批链:预算走自己的 bizType,节点可在前端改
                "'budget', '预算申请审批流'",
                "INSERT INTO wf_node",
                // 板块口径:采购归入预算管理
                "UPDATE route_module_mapping SET module = 'budget'",
                "'/budget/**'",
                // 菜单镜像 layout/menu.js
                "'预算管理'",
                "'/budget/annual'",
                "'/budget/monthly'",
                "'/budget/plan-year'",
                "'/budget/plan-month'",
                "'/budget/flow'")) {
            assertTrue(sql.contains(token), token);
        }
    }

    /**
     * 权限点必须与 BudgetController 的 @PreAuthorize 一一对应。
     * ver6.6 给采购和审批链补齐了方法级权限,预算作为新板块必须同口径,不能只靠登录兜底。
     */
    @Test
    void migrationSeedsOnePermPerBudgetEndpoint() throws Exception {
        String sql = read("/db/migration/V42__budget_management.sql");
        for (String perm : List.of(
                "budget:query", "budget:add", "budget:edit", "budget:delete",
                "budget:submit", "budget:archive", "budget:cancel")) {
            assertTrue(sql.contains("'" + perm + "'"), perm);
        }
        // 幂等三件套:权限点、admin 角色关联、admin 用户关联
        assertTrue(sql.contains("INSERT INTO sys_role_menu"), "sys_role_menu");
        assertTrue(sql.contains("INSERT INTO sys_user_role"), "sys_user_role");
    }

    /** V41 已被 ver6.6 的采购权限种子占用,预算迁移必须排在 V42,否则 Flyway 版本号重复启动失败。 */
    @Test
    void budgetMigrationDoesNotCollideWithPurPermsSeed() throws Exception {
        assertNotNull(getClass().getResource("/db/migration/V41__pur_workflow_perms_seed.sql"));
        assertNotNull(getClass().getResource("/db/migration/V42__budget_management.sql"));
        assertNotNull(getClass().getResource("/db/migration/V40__procurement.sql"));
    }

    private String read(String resource) throws Exception {
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
