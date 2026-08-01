package com.zhyq.park.system.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserContextTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleAdminIsRecognizedAsSuperAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "root",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_admin"))));

        assertTrue(new CurrentUserContext().isAdmin());
    }

    @Test
    void granularPermissionAloneIsNotSuperAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "operator",
                        null,
                        List.of(new SimpleGrantedAuthority("system:user:add"))));

        assertFalse(new CurrentUserContext().isAdmin());
    }

    @Test
    void missingAuthenticationIsNotSuperAdministrator() {
        assertFalse(new CurrentUserContext().isAdmin());
    }
}
