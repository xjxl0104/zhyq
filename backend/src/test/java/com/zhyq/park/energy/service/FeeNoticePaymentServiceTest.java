package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.mapper.FeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeeNoticePaymentServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final FeeNoticeMapper notices = mock(FeeNoticeMapper.class);
    final FeeNoticeItemMapper items = mock(FeeNoticeItemMapper.class);
    final SysFileMapper files = mock(SysFileMapper.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final FeeNoticePaymentService service = new FeeNoticePaymentService(jdbc, notices, items, files, storage);
    final FeeNotice notice = notice(11L, 7L, null);
    final MockMultipartFile file = new MockMultipartFile("file", "缴费凭证.pdf", "application/pdf", new byte[]{1, 2});

    @BeforeAll static void metadata() {
        for (Class<?> type : List.of(FeeNotice.class, FeeNoticeItem.class, SysFile.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
    }

    @BeforeEach void setup() {
        notice.setFeePeriodId(1L); notice.setTenantId(1L);
        notice.setPaidAmount(new BigDecimal("30.00")); notice.setTotalDue(new BigDecimal("70.00")); notice.setBillId(88L);
        when(notices.selectById(11L)).thenReturn(notice);
        when(notices.lockActive(11L)).thenReturn(notice);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(Long.class), eq(1L))).thenReturn(List.of(1L));
        when(storage.store(file)).thenReturn(new FileStorageService.StoredResult("2026/11/proof.pdf", "/uploads/proof.pdf", "pdf", "application/pdf", 2, "缴费凭证.pdf"));
    }

    @Test void paymentMarkReturnsFullNoticeWithoutChangingBalancesOrLinkedBill() {
        FeeNoticeItem item = new FeeNoticeItem(); item.setNoticeId(11L); item.setFeeItem("电费");
        when(items.selectList(any())).thenReturn(List.of(item));
        FeeNotice result = service.updateStatus(11L, 1);
        assertThat(result.getPaymentStatus()).isEqualTo(1);
        assertThat(result.getItems()).containsExactly(item);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getTotalDue()).isEqualByComparingTo("70.00");
        assertThat(result.getBillId()).isEqualTo(88L);
        ArgumentCaptor<LambdaUpdateWrapper<FeeNotice>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(notices).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSet()).startsWith("payment_status=").doesNotContain("paid_amount", "total_due", "bill_id");
        verify(notices, never()).updateById(any(FeeNotice.class));
        assertThat(service.updateStatus(11L, 0).getPaymentStatus()).isZero();
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {-1, 2, 99})
    void invalidStatusDoesNotReadOrWriteAnyNotice(Integer status) {
        assertThatThrownBy(() -> service.updateStatus(11L, status)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(400));
        verifyNoInteractions(notices, items, jdbc, files, storage);
    }

    @Test void missingAndRegeneratedNoticesCannotReceivePaymentOrAttachments() {
        assertThatThrownBy(() -> service.updateStatus(999L, 1)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(404));
        when(notices.lockActive(11L)).thenReturn(null);
        assertThatThrownBy(() -> service.upload(file, 11L)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(409));
        verifyNoInteractions(storage, files);
        verify(notices, never()).update(any(), any());
    }

    @Test void uploadUsesFreshNoticeAfterTheSharedPeriodLock() {
        SysFile result = service.upload(file, 11L);
        assertThat(result.getBizType()).isEqualTo(FeeNoticePaymentService.BIZ_TYPE);
        assertThat(result.getBizId()).isEqualTo(11L);
        assertThat(result.getOriginalName()).isEqualTo("缴费凭证.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        var order = inOrder(notices, jdbc, storage, files);
        order.verify(notices).selectById(11L);
        order.verify(jdbc).queryForList(contains("FOR UPDATE"), eq(Long.class), eq(1L));
        order.verify(notices).lockActive(11L);
        order.verify(storage).store(file);
        order.verify(files).insert(result);
    }

    @Test void rollbackRemovesNewPhysicalFileButCommitKeepsIt() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(file, 11L);
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(storage, never()).deletePhysical(anyString());
            callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(storage).deletePhysical("2026/11/proof.pdf");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void failedStorageDoesNotCreateAttachmentAndFailedInsertCleansStoredFile() {
        BizException failure = new BizException("文件保存失败");
        when(storage.store(file)).thenThrow(failure);
        assertThatThrownBy(() -> service.upload(file, 11L)).isSameAs(failure);
        verifyNoInteractions(files);
        doReturn(new FileStorageService.StoredResult("2026/11/proof.pdf", "/uploads/proof.pdf", "pdf", "application/pdf", 2, "缴费凭证.pdf"))
                .when(storage).store(file);
        when(files.insert(any(SysFile.class))).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.upload(file, 11L)).hasMessageContaining("database unavailable");
        verify(storage).deletePhysical("2026/11/proof.pdf");
    }

    @Test void regenerationMovesAllOldHouseholdEvidenceToTheNewId() {
        FeeNotice first = notice(11L, 7L, 1), second = notice(12L, 7L, 1), replacement = notice(21L, 7L, 1);
        SysFile evidence = new SysFile(); evidence.setBizId(12L);
        when(files.selectList(any())).thenReturn(List.of(evidence));
        var sources = service.prepareRegeneration(List.of(first, second), List.of(replacement));
        assertThat(sources.get("T7")).containsExactly(11L, 12L);
        service.moveAttachments(sources, replacement);
        ArgumentCaptor<LambdaUpdateWrapper<SysFile>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(files).update(isNull(), update.capture());
        update.getValue().getSqlSegment();
        assertThat(update.getValue().getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(FeeNoticePaymentService.BIZ_TYPE, FeeNoticePaymentService.INVOICE_BIZ_TYPE, 11L, 12L, 21L);
    }

    @Test void cannotRegenerateAwayPaidHouseholdsOrEvidenceButOrdinaryRemovedHouseholdsAreAllowed() {
        FeeNotice old = notice(11L, 7L, 1);
        assertThatThrownBy(() -> service.prepareRegeneration(List.of(old), List.of()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(409));
        old.setPaymentStatus(0);
        SysFile evidence = new SysFile(); evidence.setBizId(11L);
        when(files.selectList(any())).thenReturn(List.of(evidence));
        assertThatThrownBy(() -> service.prepareRegeneration(List.of(old), List.of())).hasMessageContaining("附件");
        when(files.selectList(any())).thenReturn(List.of());
        assertThat(service.prepareRegeneration(List.of(old), List.of())).containsKey("T7");
        verify(files, never()).update(any(), any());
    }

    @Test void deletionRequiresBothPaymentMarkAndEvidenceToBeCleared() {
        notice.setPaymentStatus(1);
        assertThatThrownBy(() -> service.assertCanDelete(List.of(notice))).hasMessageContaining("改为未缴");
        notice.setPaymentStatus(0);
        when(files.selectList(any())).thenReturn(List.of(new SysFile()));
        assertThatThrownBy(() -> service.assertCanDelete(List.of(notice))).hasMessageContaining("附件");
        when(files.selectList(any())).thenReturn(List.of());
        service.assertCanDelete(List.of(notice));
    }

    @Test void legacyNullStatusIsPresentedAsUnpaid() {
        assertThat(FeeNoticePaymentService.normalizeStatus(notice).getPaymentStatus()).isZero();
    }

    static FeeNotice notice(Long id, Long tenant, Integer status) {
        FeeNotice n = new FeeNotice(); n.setId(id); n.setTenantRefId(tenant); n.setTenantName("租户" + tenant); n.setPaymentStatus(status); return n;
    }
}
