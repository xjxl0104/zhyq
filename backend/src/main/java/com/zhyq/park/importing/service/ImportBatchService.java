package com.zhyq.park.importing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportBatchService {
    public static final String UPLOADED = "UPLOADED";
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String ROLLED_BACK = "ROLLED_BACK";

    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;

    @Transactional
    public ImportBatch createBatch(String bizType, String sourceSystem, String fileName,
                                   byte[] fileBytes, Long fileId, Long tenantId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(sourceSystem)
                || !StringUtils.hasText(fileName) || fileBytes == null || fileBytes.length == 0
                || tenantId == null) {
            throw new BizException("导入批次信息不完整");
        }
        String fileHash = ImportFileHasher.sha256(fileBytes);
        Long duplicateCount = batchMapper.selectCount(new LambdaQueryWrapper<ImportBatch>()
                .eq(ImportBatch::getTenantId, tenantId)
                .eq(ImportBatch::getBizType, bizType.trim())
                .eq(ImportBatch::getFileHash, fileHash));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BizException("该文件已导入，请勿重复提交");
        }

        ImportBatch batch = new ImportBatch();
        batch.setTenantId(tenantId);
        batch.setBizType(bizType.trim());
        batch.setSourceSystem(sourceSystem.trim());
        batch.setFileName(fileName.trim());
        batch.setFileHash(fileHash);
        batch.setFileId(fileId);
        batch.setStatus(UPLOADED);
        batch.setTotalRows(0);
        batch.setValidRows(0);
        batch.setInvalidRows(0);
        batch.setImportedRows(0);
        batchMapper.insert(batch);
        return batch;
    }

    @Transactional
    public void saveRows(Long batchId, List<ImportRow> rows) {
        requireState(batchId, Set.of(UPLOADED));
        if (rows == null) {
            throw new BizException("导入行不能为空");
        }
        for (ImportRow row : rows) {
            row.setBatchId(batchId);
            rowMapper.insert(row);
        }
    }

    @Transactional
    public void markPendingConfirm(Long batchId, int totalRows, int validRows,
                                   int invalidRows, String errorSummary) {
        if (totalRows < 0 || validRows < 0 || invalidRows < 0
                || totalRows != validRows + invalidRows) {
            throw new BizException("导入行统计不一致");
        }
        ImportBatch batch = requireState(batchId, Set.of(UPLOADED));
        batch.setStatus(PENDING_CONFIRM);
        batch.setTotalRows(totalRows);
        batch.setValidRows(validRows);
        batch.setInvalidRows(invalidRows);
        batch.setErrorSummary(errorSummary);
        update(batch);
    }

    @Transactional
    public void markCompleted(Long batchId, int importedRows, String confirmedBy) {
        ImportBatch batch = requireState(batchId, Set.of(PENDING_CONFIRM));
        if (importedRows < 0) {
            throw new BizException("导入数量不能为负数");
        }
        batch.setStatus(COMPLETED);
        batch.setImportedRows(importedRows);
        batch.setConfirmedBy(confirmedBy);
        batch.setConfirmedTime(LocalDateTime.now());
        update(batch);
    }

    @Transactional
    public void markFailed(Long batchId, String errorSummary) {
        ImportBatch batch = requireState(batchId, Set.of(UPLOADED, PENDING_CONFIRM));
        batch.setStatus(FAILED);
        batch.setErrorSummary(errorSummary);
        update(batch);
    }

    @Transactional
    public void markRolledBack(Long batchId, String rollbackBy) {
        ImportBatch batch = requireState(batchId, Set.of(COMPLETED));
        batch.setStatus(ROLLED_BACK);
        batch.setRollbackBy(rollbackBy);
        batch.setRollbackTime(LocalDateTime.now());
        update(batch);
    }

    private ImportBatch requireState(Long batchId, Set<String> expected) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException("导入批次不存在");
        }
        if (!expected.contains(batch.getStatus())) {
            throw new BizException("导入批次状态已变化，当前状态: " + batch.getStatus());
        }
        return batch;
    }

    private void update(ImportBatch batch) {
        if (batchMapper.updateById(batch) != 1) {
            throw new BizException("导入批次状态更新失败，请刷新后重试");
        }
    }
}
