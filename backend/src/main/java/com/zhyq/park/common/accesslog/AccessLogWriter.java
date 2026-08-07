package com.zhyq.park.common.accesslog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AccessLogWriter {

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200;

    private final ArrayBlockingQueue<AccessLogEntry> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong discardCount = new AtomicLong(0);
    private final JdbcTemplate jdbc;

    public AccessLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void submit(AccessLogEntry entry) {
        if (!queue.offer(entry)) {
            discardCount.incrementAndGet();
        }
    }

    public long getDiscardCount() {
        return discardCount.get();
    }

    @Scheduled(fixedDelay = 2000)
    public void flush() {
        List<AccessLogEntry> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) return;

        try {
            String sql = "INSERT INTO access_log (user_id, dept_id, route, method, module, is_core, status_code, duration_ms, created_at) VALUES (?,?,?,?,?,?,?,?,?)";
            jdbc.batchUpdate(sql, batch, BATCH_SIZE, (ps, e) -> {
                ps.setLong(1, e.getUserId());
                ps.setLong(2, e.getDeptId());
                ps.setString(3, e.getRoute());
                ps.setString(4, e.getMethod());
                ps.setString(5, e.getModule());
                ps.setBoolean(6, Boolean.TRUE.equals(e.getIsCore()));
                ps.setInt(7, e.getStatusCode());
                ps.setInt(8, e.getDurationMs());
                ps.setTimestamp(9, Timestamp.valueOf(e.getCreatedAt()));
            });
        } catch (Exception e) {
            discardCount.addAndGet(batch.size());
        }
    }
}
