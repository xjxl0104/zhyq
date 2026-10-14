package com.zhyq.park.auth;

import com.zhyq.park.finance.controller.PaymentController;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.service.FinancePaymentImportService;
import com.zhyq.park.finance.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 收款接口 RBAC 单测:直接验证 @PreAuthorize AOP 生效(不经 web 层/MyBatis)。
 * 无匹配权限 → AccessDeniedException;有权限 → 正常放行到 handler。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PaymentControllerSecurityTest.TestBeans.class)
class PaymentControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean PaymentService paymentService() { return mock(PaymentService.class); }
        @Bean PaymentMapper paymentMapper() { return mock(PaymentMapper.class); }
        @Bean FinancePaymentImportService importService() { return mock(FinancePaymentImportService.class); }
        @Bean PaymentController paymentController(PaymentService s, PaymentMapper m, FinancePaymentImportService i) {
            return new PaymentController(s, m, i);
        }
    }

    @Autowired PaymentController controller;
    @Autowired PaymentService paymentService;
    @Autowired PaymentMapper paymentMapper;
    @Autowired FinancePaymentImportService importService;

    private static final org.springframework.mock.web.MockMultipartFile FILE =
            new org.springframework.mock.web.MockMultipartFile("file", "x.csv", "text/csv", new byte[]{1});

    @Test
    @WithMockUser(authorities = "finance:bill:query")
    void 历史收款导入_无收款权限_预览与确认都拒绝() {
        assertThrows(AccessDeniedException.class, () -> controller.previewHistoryImport(FILE));
        assertThrows(AccessDeniedException.class, () -> controller.confirmHistoryImport(FILE));
        verifyNoInteractions(importService);
    }

    @Test
    @WithMockUser(authorities = "finance:payment:pay")
    void 历史收款导入_有收款权限_放行() {
        assertDoesNotThrow(() -> controller.previewHistoryImport(FILE));
        assertDoesNotThrow(() -> controller.confirmHistoryImport(FILE));
        verify(importService).preview(FILE);
        verify(importService).confirm(eq(FILE), any());
    }

    @Test
    @WithMockUser(authorities = "finance:bill:query") // 权限不匹配
    void 收款_无权限_拒绝() {
        assertThrows(AccessDeniedException.class, () -> controller.pay(Map.of()));
    }

    @Test
    @WithMockUser(authorities = "finance:payment:pay")
    void 收款_有权限_放行() {
        when(paymentService.收款(any(), any(), any(), any())).thenReturn(new Payment());
        assertDoesNotThrow(() -> controller.pay(Map.of()));
    }

    @Test
    @WithMockUser(authorities = "finance:bill:query")
    void 查收款记录_无权限_拒绝() {
        assertThrows(AccessDeniedException.class, () -> controller.list(1L));
    }

    @Test
    @WithMockUser(authorities = "finance:payment:query")
    void 查收款记录_有权限_放行() {
        when(paymentMapper.selectList(any())).thenReturn(java.util.List.of());
        assertDoesNotThrow(() -> controller.list(1L));
    }
}
