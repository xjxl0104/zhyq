package com.zhyq.park.importing;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceTest {
    @Mock
    private ImportBatchMapper batchMapper;
    @Mock
    private ImportRowMapper rowMapper;

    private ImportBatchService service;

    @BeforeEach
    void setUp() {
        service = new ImportBatchService(batchMapper, rowMapper);
    }

    @Test
    void createsUploadedBatchWithStableHash() {
        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.insert(any(ImportBatch.class))).thenAnswer(invocation -> {
            ((ImportBatch) invocation.getArgument(0)).setId(10L);
            return 1;
        });

        ImportBatch batch = service.createBatch(
                "RECEIVABLE", "EXCEL", "fixture.xlsx",
                "abc".getBytes(StandardCharsets.UTF_8), 99L, 1L);

        assertEquals(10L, batch.getId());
        assertEquals(ImportBatchService.UPLOADED, batch.getStatus());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                batch.getFileHash());
        assertEquals(0, batch.getTotalRows());
    }

    @Test
    void rejectsDuplicateFileBeforeInsert() {
        when(batchMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.createBatch(
                "RECEIVABLE", "EXCEL", "fixture.xlsx", new byte[]{1}, null, 1L));

        verify(batchMapper, never()).insert(any(ImportBatch.class));
    }

    @Test
    void savesRowsAndGuardsStatusTransitions() {
        ImportBatch uploaded = batch(10L, ImportBatchService.UPLOADED);
        when(batchMapper.selectById(10L)).thenReturn(uploaded);
        when(batchMapper.updateById(any(ImportBatch.class))).thenReturn(1);

        ImportRow valid = new ImportRow();
        valid.setStatus("VALID");
        ImportRow invalid = new ImportRow();
        invalid.setStatus("INVALID");
        service.saveRows(10L, List.of(valid, invalid));
        service.markPendingConfirm(10L, 2, 1, 1, "一行待修正");

        assertEquals(10L, valid.getBatchId());
        verify(rowMapper).insert(valid);
        verify(rowMapper).insert(invalid);

        ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batchMapper).updateById(captor.capture());
        assertEquals(ImportBatchService.PENDING_CONFIRM, captor.getValue().getStatus());
        assertEquals(2, captor.getValue().getTotalRows());
    }

    @Test
    void refusesCompletionOutsidePendingConfirm() {
        when(batchMapper.selectById(10L)).thenReturn(batch(10L, ImportBatchService.UPLOADED));

        assertThrows(BizException.class, () -> service.markCompleted(10L, 9, "admin"));
        verify(batchMapper, never()).updateById(any(ImportBatch.class));
    }

    private static ImportBatch batch(long id, String status) {
        ImportBatch batch = new ImportBatch();
        batch.setId(id);
        batch.setStatus(status);
        return batch;
    }
}
