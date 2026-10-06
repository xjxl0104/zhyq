package com.zhyq.park.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Uses real HTTP parsing: MockMvc does not enforce the server's request-header size limit. */
class JwtHeaderLimitTest {
    private static final String SECRET = "header-limit-test-secret-more-than-32-bytes";
    @TempDir Path directory;

    @Test void largeAccountCanAuthenticateThroughDefaultTomcatHeaderLimit() throws Exception {
        List<String> grants = IntStream.range(0, 500)
                .mapToObj(i -> "MENU:/business/resource" + i).toList();
        JwtAccountService accounts = mock(JwtAccountService.class);
        when(accounts.load("admin", 1L)).thenReturn(
                new JwtAccountService.Account("admin", 1L, "operator", grants, "unchanged-credential"));
        JwtService jwt = new JwtService(SECRET, 3600, accounts);
        String compactToken = jwt.issueForIdentity("admin", 1L);
        String oldToken = Jwts.builder().claims(jwt.parse(compactToken)).claim("auth", grants)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(oldToken.length()).isGreaterThan(8192);
        assertThat(compactToken.length()).isLessThan(1024);
        // Both signatures/identities are valid; only the old wire representation exceeds the limit.
        assertThat(jwt.authenticate(jwt.parse(oldToken)).authorities()).containsExactlyElementsOf(grants);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", directory.toString());
        JwtAuthFilter filter = new JwtAuthFilter(jwt);
        Tomcat.addServlet(context, "identity", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                try {
                    filter.doFilter(request, response, (req, res) -> {
                        var auth = SecurityContextHolder.getContext().getAuthentication();
                        response.setStatus(auth == null ? 401 : 200);
                        if (auth != null) response.getWriter().write(auth.getName() + ":" + auth.getAuthorities().size());
                    });
                } finally { SecurityContextHolder.clearContext(); }
            }
        });
        context.addServletMappingDecoded("/identity", "identity");
        try {
            tomcat.start();
            URI endpoint = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/identity");
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            assertThat(send(http, endpoint, oldToken).statusCode()).isEqualTo(400);
            var result = send(http, endpoint, compactToken);
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).isEqualTo("operator:500");
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private HttpResponse<String> send(HttpClient http, URI endpoint, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
