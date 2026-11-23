package com.zhyq.park.marketing.wh;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import java.lang.reflect.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WhOwnerEndpointSecurityTest {
    @Configuration @EnableMethodSecurity static class Security {}
    @Test void staffCannotInvokeAnyLegacyOwnerEndpointIncludingFilesAndFinance() throws Exception {
        List<Class<?>> ownerControllers=List.of(WhOnboardingController.class,WhFinanceController.class,WhBillingController.class,
                WhErpController.class,WhErpReconciliationController.class,WhCompetitionProtectionController.class,WhFileController.class,WhStaffController.class);
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(Security.class);
            context.registerBean(WhIdentityService.class, () -> mock(WhIdentityService.class));
            for(Class<?> type:ownerControllers) register(context,type);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wh:11:contact:5",null,List.of(new SimpleGrantedAuthority("ROLE_WH"),new SimpleGrantedAuthority("ROLE_WH_MEMBER"))));
            int checked=0;
            for(Class<?> type:ownerControllers) for(Method method:type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method,RequestMapping.class)) continue;
                Object[] args=Arrays.stream(method.getParameterTypes()).map(p->p==int.class?1:p==boolean.class?false:null).toArray();
                try { method.invoke(context.getBean(type),args); fail("Member reached owner endpoint " + type.getSimpleName() + "." + method.getName()); }
                catch(InvocationTargetException e){assertThat(e.getCause()).as(type.getSimpleName()+"."+method.getName()).isInstanceOf(AccessDeniedException.class);}
                checked++;
            }
            assertThat(checked).isGreaterThan(30);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void register(AnnotationConfigApplicationContext context,Class type) {
        context.registerBean(type,()->{
            try {var ctor=type.getConstructors()[0];return ctor.newInstance(Arrays.stream(ctor.getParameterTypes()).map(p->mock((Class)p)).toArray());}
            catch(Exception e){throw new IllegalStateException(e);}
        });
    }
}
