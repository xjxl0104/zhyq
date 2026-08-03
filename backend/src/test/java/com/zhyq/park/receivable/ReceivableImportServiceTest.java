package com.zhyq.park.receivable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportBatchService;
import com.zhyq.park.receivable.dto.ReceivableBindRequest;
import com.zhyq.park.receivable.dto.ReceivableImportPreview;
import com.zhyq.park.receivable.entity.CollectionAccount;
import com.zhyq.park.receivable.entity.DepositLedger;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import com.zhyq.park.receivable.mapper.CollectionAccountMapper;
import com.zhyq.park.receivable.mapper.DepositLedgerMapper;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import com.zhyq.park.receivable.service.ReceivableImportService;
import com.zhyq.park.receivable.service.ReceivableRuleParser;
import com.zhyq.park.receivable.service.ReceivableWorkbookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceivableImportServiceTest {
    @Mock private ImportBatchMapper batchMapper;
    @Mock private ImportRowMapper rowMapper;
    @Mock private FileStorageService fileStorageService;
    @Mock private SysFileMapper fileMapper;
    @Mock private ReceivableRegisterMapper registerMapper;
    @Mock private ReceivableRuleMapper ruleMapper;
    @Mock private DepositLedgerMapper depositMapper;
    @Mock private CollectionAccountMapper accountMapper;
    @Mock private BillMapper billMapper;

    private final List<ImportRow> storedRows = new ArrayList<>();
    private ImportBatch storedBatch;
    private ReceivableImportService service;

    @BeforeEach
    void setUp() {
        ImportBatchService batchService = new ImportBatchService(batchMapper, rowMapper);
        FieldEncryptionService encryption = new FieldEncryptionService(
                Base64.getEncoder().encodeToString(new byte[32]));
        service = new ReceivableImportService(
                new ReceivableWorkbookParser(), new ReceivableRuleParser(), batchService,
                batchMapper, rowMapper, fileStorageService, fileMapper,
                new ObjectMapper().findAndRegisterModules(), registerMapper, ruleMapper,
                depositMapper, accountMapper, billMapper, encryption);

        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.insert(any(ImportBatch.class))).thenAnswer(invocation -> {
            storedBatch = invocation.getArgument(0);
            storedBatch.setId(10L);
            return 1;
        });
        when(batchMapper.selectById(10L)).thenAnswer(invocation -> storedBatch);
        when(batchMapper.updateById(any(ImportBatch.class))).thenReturn(1);

        AtomicLong rowIds = new AtomicLong(100);
        when(rowMapper.insert(any(ImportRow.class))).thenAnswer(invocation -> {
            ImportRow row = invocation.getArgument(0);
            row.setId(rowIds.getAndIncrement());
            storedRows.add(row);
            return 1;
        });
        when(rowMapper.selectById(any(Long.class))).thenAnswer(invocation -> storedRows.stream()
                .filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(rowMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(storedRows));
        when(rowMapper.updateById(any(ImportRow.class))).thenReturn(1);

        when(fileStorageService.store(any())).thenReturn(new FileStorageService.StoredResult(
                "2026/08/fixture.xlsx", "/uploads/fixture.xlsx", "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 100L, "fixture.xlsx"));
        when(fileMapper.insert(any(SysFile.class))).thenAnswer(invocation -> {
            ((SysFile) invocation.getArgument(0)).setId(99L);
            return 1;
        });

        AtomicLong registerIds = new AtomicLong(1000);
        when(registerMapper.insert(any(ReceivableRegister.class))).thenAnswer(invocation -> {
            ((ReceivableRegister) invocation.getArgument(0)).setId(registerIds.getAndIncrement());
            return 1;
        });
        when(ruleMapper.insert(any(ReceivableRule.class))).thenReturn(1);
        when(depositMapper.insert(any(DepositLedger.class))).thenReturn(1);
        when(accountMapper.selectOne(any())).thenReturn(null);
        AtomicLong accountIds = new AtomicLong(2000);
        when(accountMapper.insert(any(CollectionAccount.class))).thenAnswer(invocation -> {
            ((CollectionAccount) invocation.getArgument(0)).setId(accountIds.getAndIncrement());
            return 1;
        });
    }

    @Test
    void previewPersistsAuditableRowsButNoBusinessRecords() throws Exception {
        ReceivableImportPreview preview = preview();

        assertEquals(9, preview.rows().size());
        assertEquals(9, preview.invalidRows());
        assertEquals(10, storedRows.size()); // 9 business rows + audited totals row
        assertTrue(storedRows.get(0).getRawJson().contains("****"));
        assertTrue(!storedRows.get(0).getRawJson().contains("622200000001"));
        verify(registerMapper, never()).insert(any(ReceivableRegister.class));
        assertThrows(BizException.class, () -> service.confirm(preview.batchId(), "admin"));
    }

    @Test
    void bindingsEnableAtomicConfirmationAndAccountDeduplication() throws Exception {
        ReceivableImportPreview preview = preview();
        for (int i = 0; i < preview.rows().size(); i++) {
            service.bindRow(preview.batchId(), new ReceivableBindRequest(
                    preview.rows().get(i).rowId(), 300L + i, 400L + i, null, 500L + i));
        }

        int imported = service.confirm(preview.batchId(), "admin");

        assertEquals(9, imported);
        assertEquals(ImportBatchService.COMPLETED, storedBatch.getStatus());
        verify(registerMapper, times(9)).insert(any(ReceivableRegister.class));
        verify(depositMapper, times(18)).insert(any(DepositLedger.class));
        verify(accountMapper, times(2)).insert(any(CollectionAccount.class));
    }

    @Test
    void rollbackRejectsBatchWithGeneratedBills() throws Exception {
        ReceivableImportPreview preview = preview();
        for (int i = 0; i < preview.rows().size(); i++) {
            service.bindRow(preview.batchId(), new ReceivableBindRequest(
                    preview.rows().get(i).rowId(), 300L + i, 400L + i, null, 500L + i));
        }
        service.confirm(preview.batchId(), "admin");
        when(billMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.rollback(preview.batchId(), "admin"));
    }

    private ReceivableImportPreview preview() throws Exception {
        byte[] bytes = ReceivableWorkbookFixture.build();
        return service.preview(new MockMultipartFile(
                "file", "fixture.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));
    }
}
