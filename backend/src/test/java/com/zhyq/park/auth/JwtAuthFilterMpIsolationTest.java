package com.zhyq.park.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class JwtAuthFilterMpIsolationTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void mpTokenIsRejectedOnBackendPath() throws Exception {
        JwtService jwt = Mockito.mock(JwtService.class);
        Claims claims = claims("mp:7");
        when(jwt.parse("mp-token")).thenReturn(claims);
        run(jwt, "mp-token", "/api/marketing/v1/customers");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void backendTokenIsRejectedOnMpPath() throws Exception {
        JwtService jwt = Mockito.mock(JwtService.class);
        Claims claims = claims("admin");
        when(jwt.parse("admin-token")).thenReturn(claims);
        run(jwt, "admin-token", "/api/mp/v1/me");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static Claims claims(String subject) {
        Claims c = Mockito.mock(Claims.class);
        when(c.getSubject()).thenReturn(subject);
        when(c.get("auth")).thenReturn(List.of("ROLE_MP"));
        return c;
    }

    private static void run(JwtService jwt, String token, String uri) throws Exception {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(req.getRequestURI()).thenReturn(uri);
        new ExposedFilter(jwt).doFilter(req, Mockito.mock(HttpServletResponse.class), Mockito.mock(FilterChain.class));
    }

    private static final class ExposedFilter extends JwtAuthFilter {
        ExposedFilter(JwtService jwt) { super(jwt); }
        @Override public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException { super.doFilterInternal(request, response, chain); }
    }
}
