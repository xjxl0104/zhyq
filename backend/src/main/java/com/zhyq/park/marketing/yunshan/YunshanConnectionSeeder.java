package com.zhyq.park.marketing.yunshan;

import com.zhyq.park.receivable.service.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 升级迁移：此前走服务端配置接口（zhyq.yunshan.*）的云仓，启动时把该接口登记为云仓自己的接口，
 * 之后每个云仓只使用自己的接口。已有云仓接口的不动，可重复执行；失败只记日志，不影响启动。
 */
@Slf4j
@Component
public class YunshanConnectionSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final YunshanClient shared;
    private final FieldEncryptionService encryption;

    public YunshanConnectionSeeder(JdbcTemplate jdbc, YunshanClient shared, FieldEncryptionService encryption) {
        this.jdbc = jdbc; this.shared = shared; this.encryption = encryption;
    }

    @Override public void run(ApplicationArguments args) {
        try { log.info("[yunshan] 登记云仓接口 {} 个", seed()); }
        catch (RuntimeException e) { log.warn("[yunshan] 登记云仓接口失败：{}", e.getClass().getSimpleName()); }
    }

    int seed() {
        if (!shared.configured()) return 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT DISTINCT w.id, w.tenant_id, w.project_id FROM crm_yunshan_volume_map m
            JOIN crm_warehouse w ON w.id = m.warehouse_id AND w.deleted = 0
            WHERE m.deleted = 0 AND m.connection_base_url IS NULL
              AND NOT EXISTS (SELECT 1 FROM crm_warehouse_yunshan_conn c WHERE c.warehouse_id = w.id AND c.deleted = 0)
            """);
        for (Map<String, Object> row : rows) {
            jdbc.update("""
                INSERT INTO crm_warehouse_yunshan_conn (warehouse_id, base_url, app_id_enc, public_key_enc, project_id, tenant_id, create_by, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'system', NOW(), NOW())
                """, row.get("id"), shared.baseUrl(), encryption.encrypt(shared.rawAppId().trim()),
                encryption.encrypt(shared.rawPublicKey().trim()), row.get("project_id"), row.get("tenant_id"));
        }
        return rows.size();
    }
}
