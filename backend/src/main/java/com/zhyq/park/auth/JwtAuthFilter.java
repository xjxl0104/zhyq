package com.zhyq.park.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 每请求一次的 JWT 过滤器:解析 Bearer token → 填充 SecurityContext。
 * 校验失败不抛异常(交给后续 authorize + EntryPoint 返回 401)。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims c = jwtService.parse(token);
                Object auth = c.get("auth");
                List<SimpleGrantedAuthority> authorities = (auth instanceof List<?> l)
                        ? l.stream().map(Object::toString).map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                        : List.of();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(c.getSubject(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignore) {
                // 无效/过期 token:不设置认证,后续 authorizeHttpRequests 拒绝 → 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
