package com.zhyq.park.common.accesslog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogWriter logWriter;
    private final ExcludeListHolder excludeHolder;
    private final RouteModuleHolder routeModuleHolder;
    private final SysUserMapper userMapper;

    public AccessLogFilter(AccessLogWriter logWriter,
                           ExcludeListHolder excludeHolder,
                           RouteModuleHolder routeModuleHolder,
                           SysUserMapper userMapper) {
        this.logWriter = logWriter;
        this.excludeHolder = excludeHolder;
        this.routeModuleHolder = routeModuleHolder;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                recordLog(request, response, System.currentTimeMillis() - start);
            } catch (Exception ignored) {
            }
        }
    }

    private void recordLog(HttpServletRequest request, HttpServletResponse response, long duration) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern : request.getRequestURI();

        if (excludeHolder.isExcluded(route)) {
            return;
        }

        String username = (String) auth.getPrincipal();
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user == null) {
            return;
        }

        RouteModuleHolder.ModuleInfo moduleInfo = routeModuleHolder.match(route);

        AccessLogEntry entry = new AccessLogEntry();
        entry.setUserId(user.getId());
        entry.setDeptId(user.getDeptId() != null ? user.getDeptId() : 0L);
        entry.setRoute(route);
        entry.setMethod(request.getMethod());
        entry.setModule(moduleInfo != null ? moduleInfo.module() : null);
        entry.setIsCore(moduleInfo != null && moduleInfo.isCore());
        entry.setStatusCode(response.getStatus());
        entry.setDurationMs((int) duration);

        logWriter.submit(entry);
    }
}
