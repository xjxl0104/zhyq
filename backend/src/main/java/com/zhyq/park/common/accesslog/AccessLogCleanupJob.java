package com.zhyq.park.common.accesslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccessLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AccessLogCleanupJob.class);
    private final JdbcTemplate jdbc;

    public AccessLogCleanupJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanup() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM access_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH) LIMIT 50000");
            if (deleted > 0) {
                log.info("AccessLog cleanup: deleted {} rows", deleted);
            }
        } catch (Exception e) {
            log.error("AccessLog cleanup failed", e);
        }
    }
}
