package com.zhyq.park.finance;

import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.entity.Payment;
import com.zhyq.park.finance.entity.Receipt;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.mapper.PaymentMapper;
import com.zhyq.park.finance.mapper.ReceiptMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import com.zhyq.park.finance.service.ReceiptVoucherService;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptVoucherServiceTest {

    @Mock private ReceiptMapper receiptMapper;
    @Mock private PaymentMapper paymentMapper;
    @Mock private BillMapper billMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ReceivableRegisterMapper receivableRegisterMapper;
    @Mock private FinanceViewEnricher viewEnricher;

    private ReceiptVoucherService service() {
        return new ReceiptVoucherService(receiptMapper, paymentMapper, billMapper, projectMapper,
                receivableRegisterMapper, viewEnricher);
    }

    @Test
    void buildsAPrintableDepositVoucherFromTheRecordedPayment() {
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        receipt.setReceiptNo("SJ-20260917-001");
        receipt.setBillId(2L);
        receipt.setPaymentId(3L);
        receipt.setTenantRefId(4L);
        receipt.setAmount(new BigDecimal("108000.00"));
        receipt.setPayee("system");
        when(receiptMapper.selectById(1L)).thenReturn(receipt);
        when(viewEnricher.resolveBillViews(List.of(2L))).thenReturn(Map.of(2L,
                new FinanceViewEnricher.BillView(2L, "RR2V1RD", "租金保证金", "广州测试租户", null,
                        new BigDecimal("108000"), null, 5)));
        Bill bill = new Bill();
        bill.setProjectId(5L);
        bill.setReceivableRegisterId(6L);
        when(billMapper.selectByIdsIncludingDeleted(List.of(2L))).thenReturn(List.of(bill));
        ReceivableRegister register = new ReceivableRegister();
        register.setSpaceNameRaw("DIPARK第五层");
        when(receivableRegisterMapper.selectById(6L)).thenReturn(register);
        Project project = new Project();
        project.setName("DIPARK数智云仓产业园");
        project.setAddress("广州市白云区测试路 1 号");
        when(projectMapper.selectById(5L)).thenReturn(project);
        Payment payment = new Payment();
        payment.setPayTime(LocalDateTime.of(2026, 9, 17, 10, 30));
        payment.setPayMethod("银行转账");
        when(paymentMapper.selectById(3L)).thenReturn(payment);

        ReceiptVoucherService.ReceiptVoucher voucher = service().voucher(1L);

        assertThat(voucher.title()).isEqualTo("保证金收据");
        assertThat(voucher.payerName()).isEqualTo("广州测试租户");
        assertThat(voucher.amountUppercase()).isEqualTo("壹拾万捌仟元整");
        assertThat(voucher.issuerName()).isEqualTo("DIPARK数智云仓产业园");
        assertThat(voucher.contractNo()).isEqualTo("RR2V1RD");
        assertThat(voucher.leaseAddress()).isEqualTo("DIPARK第五层");
        assertThat(voucher.payee()).isEmpty();
    }

    @Test
    void rejectsPrintingAVoidedReceipt() {
        Receipt receipt = new Receipt();
        receipt.setId(9L);
        receipt.setVoidStatus(1);
        when(receiptMapper.selectById(9L)).thenReturn(receipt);

        assertThatThrownBy(() -> service().voucher(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已作废");
    }

    @Test
    void formatsChineseCurrencyWithDecimalPlaces() {
        assertThat(ReceiptVoucherService.toChineseCurrency(new BigDecimal("300.05")))
                .isEqualTo("叁佰元零伍分");
        assertThat(ReceiptVoucherService.toChineseCurrency(BigDecimal.ZERO)).isEqualTo("零元整");
    }
}
