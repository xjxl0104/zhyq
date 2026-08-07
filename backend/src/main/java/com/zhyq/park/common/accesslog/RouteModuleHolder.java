package com.zhyq.park.common.accesslog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RouteModuleHolder {

    private final JdbcTemplate jdbc;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final CopyOnWriteArrayList<RouteMapping> mappings = new CopyOnWriteArrayList<>();

    public RouteModuleHolder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 300_000)
    public void reload() {
        List<RouteMapping> list = jdbc.query(
                "SELECT route, module, is_core FROM route_module_mapping WHERE enabled = 1",
                (rs, i) -> new RouteMapping(rs.getString("route"), rs.getString("module"), rs.getBoolean("is_core")));
        mappings.clear();
        mappings.addAll(list);
    }

    public ModuleInfo match(String route) {
        for (RouteMapping m : mappings) {
            if (matcher.match(m.pattern(), route)) {
                return new ModuleInfo(m.module(), m.isCore());
            }
        }
        return null;
    }

    public record ModuleInfo(String module, boolean isCore) {}
    private record RouteMapping(String pattern, String module, boolean isCore) {}
}
