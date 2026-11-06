package com.zhyq.park.common.audit;

import com.zhyq.park.common.audit.mapper.OperLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** sys_oper_log.operator 记当前登录用户名,没有登录上下文(定时任务)才记 system。 */
class AuditAspectOperatorTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 登录用户_记用户名() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "dingchao", null, List.of(new SimpleGrantedAuthority("ROLE_admin"))));
        assertEquals("dingchao", operatorOf(run()));
    }

    @Test
    void 无登录上下文_记system() throws Throwable {
        assertEquals("system", operatorOf(run()));
    }

    private OperLogMapper run() throws Throwable {
        OperLogMapper mapper = mock(OperLogMapper.class);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getDeclaringType()).thenReturn(AuditAspectOperatorTest.class);
        when(sig.getName()).thenReturn("save");
        when(pjp.getArgs()).thenReturn(new Object[0]);
        OperationLog ann = mock(OperationLog.class);
        when(ann.module()).thenReturn("工单");
        when(ann.action()).thenReturn("新增");
        when(ann.saveParams()).thenReturn(false);
        new AuditAspect(mapper).around(pjp, ann);
        return mapper;
    }

    private static String operatorOf(OperLogMapper mapper) {
        ArgumentCaptor<OperLog> captor = ArgumentCaptor.forClass(OperLog.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue().getOperator();
    }
}
