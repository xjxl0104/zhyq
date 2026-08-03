package com.zhyq.park.vending;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportBatchService;
import com.zhyq.park.vending.dto.VendingExcludeRowsRequest;
import com.zhyq.park.vending.dto.VendingImportPreview;
import com.zhyq.park.vending.entity.VendingMachine;
import com.zhyq.park.vending.entity.VendingSale;
import com.zhyq.park.vending.mapper.VendingFaultMapper;
import com.zhyq.park.vending.mapper.VendingMachineMapper;
import com.zhyq.park.vending.mapper.VendingReconciliationMapper;
import com.zhyq.park.vending.mapper.VendingRestockMapper;
import com.zhyq.park.vending.mapper.VendingSaleMapper;
import com.zhyq.park.vending.model.VendingImportType;
import com.zhyq.park.vending.service.VendingImportParser;
import com.zhyq.park.vending.service.VendingImportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendingImportServiceTest {
    @Mock private ImportBatchMapper batchMapper;
    @Mock private ImportRowMapper rowMapper;
    @Mock private VendingMachineMapper machineMapper;
    @Mock private VendingSaleMapper saleMapper;
    @Mock private VendingRestockMapper restockMapper;
    @Mock private VendingFaultMapper faultMapper;
    @Mock private VendingReconciliationMapper reconciliationMapper;

    private final List<ImportRow> storedRows = new ArrayList<>();
    private ImportBatch storedBatch;
    private VendingImportService service;

    @BeforeEach
    void setUp() {
        ImportBatchService batches = new ImportBatchService(batchMapper, rowMapper);
        service = new VendingImportService(
                new VendingImportParser(), batches, batchMapper, rowMapper,
                new ObjectMapper().findAndRegisterModules(), machineMapper, saleMapper,
                restockMapper, faultMapper, reconciliationMapper);

        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.insert(any(ImportBatch.class))).thenAnswer(invocation -> {
            storedBatch = invocation.getArgument(0);
            storedBatch.setId(10L);
            return 1;
        });
        when(batchMapper.selectById(10L)).thenAnswer(invocation -> storedBatch);
        when(batchMapper.updateById(any(ImportBatch.class))).thenReturn(1);

        AtomicLong ids = new AtomicLong(100);
        when(rowMapper.insert(any(ImportRow.class))).thenAnswer(invocation -> {
            ImportRow row = invocation.getArgument(0);
            row.setId(ids.getAndIncrement());
            storedRows.add(row);
            return 1;
        });
        when(rowMapper.selectById(any(Long.class))).thenAnswer(invocation -> storedRows.stream()
                .filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(rowMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(storedRows));
        when(rowMapper.updateById(any(ImportRow.class))).thenReturn(1);

        when(machineMapper.selectOne(any())).thenReturn(null);
        when(machineMapper.insert(any(VendingMachine.class))).thenAnswer(invocation -> {
            ((VendingMachine) invocation.getArgument(0)).setId(1000L);
            return 1;
        });
        when(saleMapper.insert(any(VendingSale.class))).thenAnswer(invocation -> {
            ((VendingSale) invocation.getArgument(0)).setId(2000L);
            return 1;
        });
        when(saleMapper.updateById(any(VendingSale.class))).thenReturn(1);
    }

    @Test
    void previewOnlyAuditsRowsThenExcludedErrorsAllowConfirmation() throws Exception {
        byte[] bytes = workbook(VendingImportType.MACHINE,
                new String[]{"M-01", "一号机", "A栋", "FJ", "在线", "2026-08-01 09:30:00"},
                new String[]{"", "错误机", "B栋", "FJ", "在线", "2026-08-01 09:30:00"});

        VendingImportPreview preview = service.preview(VendingImportType.MACHINE,
                file("machine.xlsx", bytes));

        assertEquals(2, preview.totalRows());
        assertEquals(1, preview.validRows());
        assertEquals(1, preview.invalidRows());
        assertEquals(ImportBatchService.PENDING_CONFIRM, preview.status());
        verify(machineMapper, never()).insert(any(VendingMachine.class));
        assertThrows(BizException.class, () -> service.confirm(preview.batchId(), "admin"));

        long invalidId = preview.rows().stream()
                .filter(row -> "INVALID".equals(row.status())).findFirst().orElseThrow().rowId();
        service.excludeRows(preview.batchId(), new VendingExcludeRowsRequest(List.of(invalidId), "源文件错误"));
        assertEquals(1, service.confirm(preview.batchId(), "admin"));
        verify(machineMapper).insert(any(VendingMachine.class));
        assertEquals("EXCLUDED", storedRows.get(1).getStatus());
    }

    @Test
    void confirmUpsertsByStableSaleKeyAndRefreshesSourceBatch() throws Exception {
        VendingSale existing = new VendingSale();
        existing.setId(88L);
        existing.setVersion(3);
        existing.setProductName("旧商品");
        existing.setSourceBatchId(5L);
        when(saleMapper.selectOne(any())).thenReturn(existing);
        byte[] bytes = workbook(VendingImportType.SALE,
                new String[]{"O-01", "1", "M-01", "P-01", "矿泉水", "2", "6", "1", "5",
                        "微信", "2026-08-01 10:30:00", "已完成"});

        VendingImportPreview preview = service.preview(VendingImportType.SALE, file("sale.xlsx", bytes));
        assertEquals(1, service.confirm(preview.batchId(), "admin"));

        verify(saleMapper, never()).insert(any(VendingSale.class));
        verify(saleMapper).updateById(any(VendingSale.class));
        assertEquals(10L, storedRows.get(0).getBatchId());
        assertEquals("IMPORTED", storedRows.get(0).getStatus());
        assertTrue(storedRows.get(0).getRawJson().contains("beforeImage"));

        when(saleMapper.selectById(88L)).thenReturn(existing);
        service.rollback(preview.batchId(), "admin");
        verify(saleMapper, org.mockito.Mockito.times(2)).updateById(any(VendingSale.class));
    }

    @Test
    void duplicateFileHashIsRejectedBeforeAnyBusinessWrite() throws Exception {
        when(batchMapper.selectCount(any())).thenReturn(1L);
        byte[] bytes = workbook(VendingImportType.MACHINE,
                new String[]{"M-01", "一号机", "A栋", "FJ", "在线", "2026-08-01 09:30:00"});

        assertThrows(BizException.class,
                () -> service.preview(VendingImportType.MACHINE, file("machine.xlsx", bytes)));
        verify(machineMapper, never()).insert(any(VendingMachine.class));
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private byte[] workbook(VendingImportType type, String[]... rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(type.sheetName());
            sheet.createRow(0).createCell(0).setCellValue("智慧园区自动售货机标准导入模板");
            var header = sheet.createRow(1);
            for (int i = 0; i < type.headers().size(); i++) {
                header.createCell(i).setCellValue(type.headers().get(i));
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 2);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
