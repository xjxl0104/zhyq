package com.zhyq.park.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 token 拦截:除登录与文档外,/** 一律要求 Authorization: Bearer <token>。
 */
@Configuration
@RequiredArgsConstructor
public class AuthInterceptorConfig implements WebMvcConfigurer {

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull Object handler) throws Exception {
                // 预检请求直接放行
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                String auth = request.getHeader("Authorization");
                String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
                if (tokenStore.validate(token) != null) {
                    return true;
                }
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        Result.fail(401, "未登录或登录已过期")));
                return false;
            }
        })
        .addPathPatterns("/**")
        .excludePathPatterns(
                "/auth/login",
                // 接口文档(演示环境保留,方便看接口;正式环境应一并拦截)
                "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**",
                "/favicon.ico", "/error"
        );
    }
}
