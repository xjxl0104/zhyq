package com.zhyq.park.marketing.wh;

import com.zhyq.park.auth.JwtAuthFilter;
import com.zhyq.park.auth.JwtService;
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

class WhAuthIsolationTest {
  @AfterEach void clear(){SecurityContextHolder.clearContext();}
  @Test void warehouseTokenCannotCrossIntoPartnerOrCrm(){
    JwtService jwt=Mockito.mock(JwtService.class); Claims c=Mockito.mock(Claims.class);
    when(c.getSubject()).thenReturn("wh:11"); when(c.get("auth")).thenReturn(List.of("ROLE_WH")); when(jwt.parse("t")).thenReturn(c);
    run(jwt,"/api/mp/v1/me"); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    run(jwt,"/api/crm/marketing/dashboard"); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
  @Test void warehouseTokenAuthenticatesOnlyWarehousePath(){
    JwtService jwt=Mockito.mock(JwtService.class); Claims c=Mockito.mock(Claims.class);
    when(c.getSubject()).thenReturn("wh:11"); when(c.get("auth")).thenReturn(List.of()); when(jwt.parse("t")).thenReturn(c);
    run(jwt,"/api/wh/v1/dashboard");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("wh:11");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(Object::toString).contains("ROLE_WH");
  }
  private static void run(JwtService jwt,String uri){
    try { HttpServletRequest req=Mockito.mock(HttpServletRequest.class); when(req.getHeader("Authorization")).thenReturn("Bearer t"); when(req.getRequestURI()).thenReturn(uri); new Exposed(jwt).doFilter(req,Mockito.mock(HttpServletResponse.class),Mockito.mock(FilterChain.class)); } catch(Exception e){throw new RuntimeException(e);}
  }
  static class Exposed extends JwtAuthFilter { Exposed(JwtService j){super(j);} }
}
