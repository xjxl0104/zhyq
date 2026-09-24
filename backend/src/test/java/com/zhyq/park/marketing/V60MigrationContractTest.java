package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V60 修复软删除锁记录释放 active_key 的迁移契约。 */
class V60MigrationContractTest {

    private static final String V60 = "/db/migration/V60__marketing_customer_lock_active_key.sql";

    @Test
    void activeKeyOnlyCoversNonDeletedActiveLocks() throws Exception {
        String sql = read(V60);
        assertTrue(sql.contains("MODIFY COLUMN active_key"), "V60 应重定义 active_key 生成列");
        assertTrue(sql.contains("deleted = 0 AND status IN (1, 2)"),
                "active_key 必须排除软删除记录,避免历史锁占用唯一键");
        assertTrue(sql.contains("STORED"), "active_key 应保持 STORED 生成列");
    }

    private static String read(String path) throws Exception {
        try (InputStream in = V60MigrationContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "迁移文件不存在: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
