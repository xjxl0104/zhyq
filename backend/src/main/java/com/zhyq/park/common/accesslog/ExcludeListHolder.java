package com.zhyq.park.common.accesslog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExcludeListHolder {

    private final JdbcTemplate jdbc;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final CopyOnWriteArrayList<String> patterns = new CopyOnWriteArrayList<>();

    public ExcludeListHolder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 300_000)
    public void reload() {
        List<String> list = jdbc.queryForList(
                "SELECT pattern FROM access_log_exclude WHERE enabled = 1", String.class);
        patterns.clear();
        patterns.addAll(list);
    }

    public boolean isExcluded(String route) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, route)) {
                return true;
            }
        }
        return false;
    }
}
