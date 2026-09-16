package com.zhyq.park.finance;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.controller.InvoiceController;
import com.zhyq.park.finance.entity.Invoice;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.InvoiceMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发票更正只放在未开票阶段，已开票记录必须通过红冲保留审计链。 */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock private InvoiceMapper invoiceMapper;
    @Mock private BillMapper billMapper;
    @Mock private FinanceViewEnricher viewEnricher;

    private InvoiceController controller() {
        return new InvoiceController(invoiceMapper, billMapper, viewEnricher);
    }

    @Test
    void editingAnApprovedInvoiceReturnsItToApplication() {
        Invoice existing = invoice(12L, 2);
        when(invoiceMapper.selectById(12L)).thenReturn(existing);

        Invoice request = invoice(12L, 2);
        request.setTitle("修正后的发票抬头");
        request.setAmount(new BigDecimal("4900.00"));
        when(invoiceMapper.update(any(Invoice.class), any(Wrapper.class))).thenReturn(1);

        controller().update(request);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).update(saved.capture(), any(Wrapper.class));
        assertThat(saved.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void pendingInvoiceCanBeDeleted() {
        Invoice existing = invoice(13L, 1);
        when(invoiceMapper.selectById(13L)).thenReturn(existing);
        when(invoiceMapper.delete(any(Wrapper.class))).thenReturn(1);

        controller().delete(13L);

        verify(invoiceMapper).delete(any(Wrapper.class));
    }

    @Test
    void issuedInvoiceCannotBeDeleted() {
        when(invoiceMapper.selectById(14L)).thenReturn(invoice(14L, 3));

        assertThatThrownBy(() -> controller().delete(14L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能删除");
        verify(invoiceMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void issuedInvoiceCannotBeEdited() {
        when(invoiceMapper.selectById(15L)).thenReturn(invoice(15L, 3));
        Invoice request = invoice(15L, 3);
        request.setRemark("修正备注");

        assertThatThrownBy(() -> controller().update(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能直接修改");
        verify(invoiceMapper, never()).update(any(Invoice.class), any(Wrapper.class));
    }

    private static Invoice invoice(Long id, int status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setStatus(status);
        invoice.setTitle("原抬头");
        invoice.setInvoiceType("普票");
        invoice.setAmount(new BigDecimal("100.00"));
        return invoice;
    }
}
