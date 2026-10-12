package com.zhyq.park.marketing.job;

import com.zhyq.park.marketing.service.MktWarehouseDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MktWarehouseDeletionJob {
    private final JdbcTemplate jdbc;
    private final MktWarehouseDeletionService deletion;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void run() {
        for (Long id : jdbc.queryForList("SELECT id FROM crm_warehouse WHERE deleted=0 AND join_status=8 AND delete_due_at<=NOW() ORDER BY delete_due_at LIMIT 100", Long.class)) {
            try { deletion.completeDue(id); }
            catch (Exception e) { log.error("云仓到期删除失败 warehouseId={}", id, e); }
        }
    }
}
