package com.zhyq.park.marketing.password;

import com.zhyq.park.auth.*;
import com.zhyq.park.common.accesslog.*;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static com.zhyq.park.marketing.password.PasswordAuthService.Identity.MP;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real request matchers and JWT path isolation; auth/** must not expose setup/status. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PasswordAuthSecurityTest.Beans.class)
class PasswordAuthSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class Beans {
        @Bean PasswordAuthService service() { return mock(PasswordAuthService.class); }
        @Bean PasswordAuthController controller(PasswordAuthService service) {
            return new PasswordAuthController(service, new ClientAddressResolver("172.24.0.5/32,172.24.0.2/32"));
        }
        @Bean JwtService jwtService() { return new JwtService("test-secret-for-password-security-tests-long", 3600); }
        @Bean JwtAuthFilter jwtFilter(JwtService jwt) { return new JwtAuthFilter(jwt); }
        @Bean RestAuthEntryPoint entryPoint() { return new RestAuthEntryPoint(); }
        @Bean RestAccessDeniedHandler accessDeniedHandler() { return new RestAccessDeniedHandler(); }
        @Bean AccessLogFilter logFilter() {
            return new AccessLogFilter(mock(AccessLogWriter.class), mock(ExcludeListHolder.class),
                    mock(RouteModuleHolder.class), mock(SysUserMapper.class));
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired PasswordAuthService service;
    @Autowired JwtService jwt;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousCannotReadOrSetCredentialsOnEitherPortal() throws Exception {
        for (String prefix : List.of("mp", "wh")) {
            mvc.perform(post("/" + prefix + "/v1/auth/password-setup").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}")).andExpect(status().isUnauthorized());
            mvc.perform(get("/" + prefix + "/v1/auth/password-status")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test void otherPortalAndAdministratorTokensCannotSetPassword() throws Exception {
        String warehouse = jwt.issue(8L, "wh:8", List.of("ROLE_WH"));
        String admin = jwt.issue(1L, "admin", List.of("ROLE_ADMIN"));
        String partner = jwt.issue(9L, "mp:9", List.of("ROLE_MP"));
        for (String token : List.of(warehouse, admin)) {
            mvc.perform(post("/mp/v1/auth/password-setup").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/wh/v1/auth/password-setup").header("Authorization", "Bearer " + partner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void authenticatedSamePortalCanConfigurePassword() throws Exception {
        String partner = jwt.issue(9L, "mp:9", List.of("ROLE_MP"));
        mvc.perform(post("/mp/v1/auth/password-setup").header("Authorization", "Bearer " + partner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("code").value(0));
        verify(service).setup(eq(MP), argThat(body -> "demo_user".equals(body.username()) && "Passw0rd123".equals(body.password())), eq("127.0.0.1"));
    }

    @Test void loginAndRegistrationRemainPublic() throws Exception {
        for (String prefix : List.of("mp", "wh")) {
            for (String endpoint : List.of("password-login", "password-register")) {
                mvc.perform(post("/" + prefix + "/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}"))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test void verifiedCaddyNginxChainPassesSeparateClientAddressesToRateLimiter() throws Exception {
        for (String client : List.of("198.51.100.21", "198.51.100.22")) {
            mvc.perform(post("/mp/v1/auth/password-login").with(request -> { request.setRemoteAddr("172.24.0.5"); return request; })
                    .header("X-Forwarded-For", "192.0.2.250, " + client + ", 172.24.0.2")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"demo_user\",\"password\":\"Passw0rd123\"}"))
                    .andExpect(status().isOk());
            verify(service).login(eq(MP), any(), eq(client));
        }
    }
}
