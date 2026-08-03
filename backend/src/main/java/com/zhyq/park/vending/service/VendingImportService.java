package com.zhyq.park.vending.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportBatchService;
import com.zhyq.park.importing.service.ImportFileHasher;
import com.zhyq.park.vending.dto.VendingExcludeRowsRequest;
import com.zhyq.park.vending.dto.VendingImportPreview;
import com.zhyq.park.vending.entity.VendingFault;
import com.zhyq.park.vending.entity.VendingMachine;
import com.zhyq.park.vending.entity.VendingReconciliation;
import com.zhyq.park.vending.entity.VendingRestock;
import com.zhyq.park.vending.entity.VendingSale;
import com.zhyq.park.vending.mapper.VendingFaultMapper;
import com.zhyq.park.vending.mapper.VendingMachineMapper;
import com.zhyq.park.vending.mapper.VendingReconciliationMapper;
import com.zhyq.park.vending.mapper.VendingRestockMapper;
import com.zhyq.park.vending.mapper.VendingSaleMapper;
import com.zhyq.park.vending.model.VendingImportData;
import com.zhyq.park.vending.model.VendingImportType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VendingImportService {
    private static final String SOURCE_SYSTEM = "standard_template";
    private static final String VALID = "VALID";
    private static final String INVALID = "INVALID";
    private static final String EXCLUDED = "EXCLUDED";
    private static final String IMPORTED = "IMPORTED";
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy/M/d"));

    private final VendingImportParser parser;
    private final ImportBatchService batchService;
    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;
    private final ObjectMapper objectMapper;
    private final VendingMachineMapper machineMapper;
    private final VendingSaleMapper saleMapper;
    private final VendingRestockMapper restockMapper;
    private final VendingFaultMapper faultMapper;
    private final VendingReconciliationMapper reconciliationMapper;

    @Transactional(rollbackFor = Exception.class)
    public VendingImportPreview preview(VendingImportType type, MultipartFile file) {
        return preview(type, file, 1L);
    }

    @Transactional(rollbackFor = Exception.class)
    public VendingImportPreview preview(VendingImportType type, MultipartFile file, Long tenantId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择售货机标准模板文件");
        }
        String originalName = StringUtils.cleanPath(
                Optional.ofNullable(file.getOriginalFilename()).orElse(type.bizType() + ".xlsx"));
        if (!originalName.toLowerCase().endsWith(".xlsx") && !originalName.toLowerCase().endsWith(".xls")) {
            throw new BizException("售货机导入仅支持 .xlsx 或 .xls 文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("无法读取上传文件");
        }

        VendingImportData data = parser.parse(type, bytes);
        ImportBatch batch = batchService.createBatch(
                type.bizType(), SOURCE_SYSTEM, originalName, bytes, null, tenantId);
        List<ImportRow> rows = toImportRows(batch, data);
        batchService.saveRows(batch.getId(), rows);
        int valid = (int) rows.stream().filter(row -> VALID.equals(row.getStatus())).count();
        int invalid = rows.size() - valid;
        batchService.markPendingConfirm(batch.getId(), rows.size(), valid, invalid,
                invalid == 0 ? null : invalid + " 行存在错误，请修正源文件或排除后确认");
        batch.setStatus(ImportBatchService.PENDING_CONFIRM);
        batch.setTotalRows(rows.size());
        batch.setValidRows(valid);
        batch.setInvalidRows(invalid);
        return toPreview(batch, rows);
    }

    @Transactional
    public VendingImportPreview excludeRows(Long batchId, VendingExcludeRowsRequest request) {
        ImportBatch batch = requireBatch(batchId, ImportBatchService.PENDING_CONFIRM);
        if (request == null || request.rowIds() == null || request.rowIds().isEmpty()
                || !StringUtils.hasText(request.reason())) {
            throw new BizException("请选择错误行并填写排除原因");
        }
        Set<Long> ids = new HashSet<>(request.rowIds());
        List<ImportRow> rows = rows(batchId);
        for (Long id : ids) {
            ImportRow row = rows.stream().filter(item -> id.equals(item.getId())).findFirst()
                    .orElseThrow(() -> new BizException("导入行不存在或不属于该批次: " + id));
            if (!INVALID.equals(row.getStatus())) {
                throw new BizException("只能排除校验失败的行: " + row.getRowNo());
            }
            row.setStatus(EXCLUDED);
            row.setErrorMessage("已排除：" + request.reason().trim());
            if (rowMapper.updateById(row) != 1) {
                throw new BizException("排除导入行失败，请刷新后重试");
            }
        }
        refreshBatchCounts(batch, rows);
        return toPreview(batch, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public int confirm(Long batchId, String confirmedBy) {
        ImportBatch batch = requireBatch(batchId, ImportBatchService.PENDING_CONFIRM);
        VendingImportType type = VendingImportType.fromBizType(batch.getBizType());
        List<ImportRow> rows = rows(batchId);
        if (rows.stream().anyMatch(row -> INVALID.equals(row.getStatus()))) {
            throw new BizException("仍有未排除的错误行，不能确认导入");
        }
        int imported = 0;
        for (ImportRow row : rows) {
            if (EXCLUDED.equals(row.getStatus())) {
                continue;
            }
            if (!VALID.equals(row.getStatus())) {
                throw new BizException("导入行状态已变化，请刷新后重试");
            }
            Map<String, String> values = readValues(row.getNormalizedJson());
            Long targetId = switch (type) {
                case MACHINE -> upsertMachine(batch, values);
                case SALE -> upsertSale(batch, values);
                case RESTOCK -> upsertRestock(batch, values);
                case FAULT -> upsertFault(batch, values);
                case RECONCILIATION -> upsertReconciliation(batch, values);
            };
            row.setStatus(IMPORTED);
            row.setTargetType(type.name());
            row.setTargetId(targetId);
            row.setErrorMessage(null);
            if (rowMapper.updateById(row) != 1) {
                throw new BizException("导入行状态更新失败");
            }
            imported++;
        }
        batchService.markCompleted(batchId, imported,
                StringUtils.hasText(confirmedBy) ? confirmedBy : "system");
        return imported;
    }

    @Transactional
    public void rollback(Long batchId, String rollbackBy) {
        requireBatch(batchId, ImportBatchService.COMPLETED);
        machineMapper.selectList(new LambdaQueryWrapper<VendingMachine>()
                        .eq(VendingMachine::getSourceBatchId, batchId))
                .forEach(entity -> machineMapper.deleteById(entity.getId()));
        saleMapper.selectList(new LambdaQueryWrapper<VendingSale>()
                        .eq(VendingSale::getSourceBatchId, batchId))
                .forEach(entity -> saleMapper.deleteById(entity.getId()));
        restockMapper.selectList(new LambdaQueryWrapper<VendingRestock>()
                        .eq(VendingRestock::getSourceBatchId, batchId))
                .forEach(entity -> restockMapper.deleteById(entity.getId()));
        faultMapper.selectList(new LambdaQueryWrapper<VendingFault>()
                        .eq(VendingFault::getSourceBatchId, batchId))
                .forEach(entity -> faultMapper.deleteById(entity.getId()));
        reconciliationMapper.selectList(new LambdaQueryWrapper<VendingReconciliation>()
                        .eq(VendingReconciliation::getSourceBatchId, batchId))
                .forEach(entity -> reconciliationMapper.deleteById(entity.getId()));
        for (ImportRow row : rows(batchId)) {
            if (IMPORTED.equals(row.getStatus())) {
                row.setStatus(ImportBatchService.ROLLED_BACK);
                rowMapper.updateById(row);
            }
        }
        batchService.markRolledBack(batchId,
                StringUtils.hasText(rollbackBy) ? rollbackBy : "system");
    }

    private List<ImportRow> toImportRows(ImportBatch batch, VendingImportData data) {
        List<ImportRow> result = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (VendingImportData.RowData source : data.rows()) {
            List<String> keys = data.type().keyHeaders().stream()
                    .map(header -> source.values().getOrDefault(header, "")).toList();
            String fingerprint = ImportFileHasher.rowFingerprint(data.type().bizType(), keys);
            List<String> errors = new ArrayList<>(source.errors());
            if (!fingerprints.add(fingerprint)) {
                errors.add("文件内业务键重复");
            }
            ImportRow row = new ImportRow();
            row.setTenantId(batch.getTenantId());
            row.setSheetName(data.type().sheetName());
            row.setRowNo(source.rowNo());
            row.setRowFingerprint(fingerprint);
            row.setRawJson(write(Map.of("values", source.values(), "formulas", source.formulas())));
            row.setNormalizedJson(write(source.values()));
            row.setStatus(errors.isEmpty() ? VALID : INVALID);
            row.setErrorMessage(errors.isEmpty() ? null : String.join("；", errors));
            result.add(row);
        }
        return result;
    }

    private Long upsertMachine(ImportBatch batch, Map<String, String> row) {
        VendingMachine value = new VendingMachine();
        value.setTenantId(batch.getTenantId());
        value.setVendorMachineId(row.get("厂商机器编号"));
        value.setMachineName(row.get("机器名称"));
        value.setSiteName(blankToNull(row.get("点位")));
        value.setModel(blankToNull(row.get("型号")));
        value.setRunningStatus(row.get("运行状态"));
        value.setLastOnlineTime(dateTimeNullable(row.get("最后在线时间")));
        value.setSourceBatchId(batch.getId());
        VendingMachine existing = findMachine(batch.getTenantId(), value.getVendorMachineId());
        return persist(value, existing, machineMapper::insert, machineMapper::updateById,
                () -> findMachine(batch.getTenantId(), value.getVendorMachineId()));
    }

    private Long upsertSale(ImportBatch batch, Map<String, String> row) {
        VendingSale value = new VendingSale();
        value.setTenantId(batch.getTenantId());
        value.setVendorOrderId(row.get("厂商订单号"));
        value.setLineNo(integer(row.get("行号")));
        value.setVendorMachineId(row.get("机器编号"));
        value.setProductId(blankToNull(row.get("商品编号")));
        value.setProductName(row.get("商品名称"));
        value.setQuantity(integer(row.get("数量")));
        value.setOriginalAmount(decimal(row.get("原价金额")));
        value.setDiscountAmount(decimal(row.get("优惠金额")));
        value.setPaidAmount(decimal(row.get("实付金额")));
        value.setPaymentMethod(blankToNull(row.get("支付方式")));
        value.setPaymentTime(dateTime(row.get("支付时间")));
        value.setOrderStatus(row.get("订单状态"));
        value.setSourceBatchId(batch.getId());
        VendingSale existing = findSale(batch.getTenantId(), value.getVendorOrderId(), value.getLineNo());
        return persist(value, existing, saleMapper::insert, saleMapper::updateById,
                () -> findSale(batch.getTenantId(), value.getVendorOrderId(), value.getLineNo()));
    }

    private Long upsertRestock(ImportBatch batch, Map<String, String> row) {
        VendingRestock value = new VendingRestock();
        value.setTenantId(batch.getTenantId());
        value.setVendorRestockId(row.get("厂商补货单号"));
        value.setVendorMachineId(row.get("机器编号"));
        value.setProductId(blankToNull(row.get("商品编号")));
        value.setProductName(row.get("商品名称"));
        value.setQuantity(integer(row.get("补货数量")));
        value.setOperatorName(blankToNull(row.get("补货人")));
        value.setRestockTime(dateTime(row.get("补货时间")));
        value.setSourceBatchId(batch.getId());
        VendingRestock existing = findRestock(batch.getTenantId(), value.getVendorRestockId());
        return persist(value, existing, restockMapper::insert, restockMapper::updateById,
                () -> findRestock(batch.getTenantId(), value.getVendorRestockId()));
    }

    private Long upsertFault(ImportBatch batch, Map<String, String> row) {
        VendingFault value = new VendingFault();
        value.setTenantId(batch.getTenantId());
        value.setVendorFaultId(row.get("厂商故障编号"));
        value.setVendorMachineId(row.get("机器编号"));
        value.setFaultType(row.get("故障类型"));
        value.setOccurredTime(dateTime(row.get("发生时间")));
        value.setRecoveredTime(dateTimeNullable(row.get("恢复时间")));
        value.setFaultStatus(row.get("状态"));
        value.setDescription(blankToNull(row.get("描述")));
        value.setSourceBatchId(batch.getId());
        VendingFault existing = findFault(batch.getTenantId(), value.getVendorFaultId());
        return persist(value, existing, faultMapper::insert, faultMapper::updateById,
                () -> findFault(batch.getTenantId(), value.getVendorFaultId()));
    }

    private Long upsertReconciliation(ImportBatch batch, Map<String, String> row) {
        VendingReconciliation value = new VendingReconciliation();
        value.setTenantId(batch.getTenantId());
        value.setVendorSettlementId(row.get("厂商结算单号"));
        value.setPeriodStart(date(row.get("结算周期开始")));
        value.setPeriodEnd(date(row.get("结算周期结束")));
        value.setSalesAmount(decimal(row.get("销售总额")));
        value.setRefundAmount(decimal(row.get("退款")));
        value.setPlatformFee(decimal(row.get("平台费用")));
        value.setNetAmount(decimal(row.get("结算净额")));
        value.setSettlementStatus(row.get("状态"));
        value.setSourceBatchId(batch.getId());
        VendingReconciliation existing = findReconciliation(batch.getTenantId(), value.getVendorSettlementId());
        return persist(value, existing, reconciliationMapper::insert, reconciliationMapper::updateById,
                () -> findReconciliation(batch.getTenantId(), value.getVendorSettlementId()));
    }

    private VendingMachine findMachine(Long tenantId, String id) {
        return machineMapper.selectOne(new LambdaQueryWrapper<VendingMachine>()
                .eq(VendingMachine::getTenantId, tenantId).eq(VendingMachine::getVendorMachineId, id));
    }

    private VendingSale findSale(Long tenantId, String id, Integer lineNo) {
        return saleMapper.selectOne(new LambdaQueryWrapper<VendingSale>()
                .eq(VendingSale::getTenantId, tenantId).eq(VendingSale::getVendorOrderId, id)
                .eq(VendingSale::getLineNo, lineNo));
    }

    private VendingRestock findRestock(Long tenantId, String id) {
        return restockMapper.selectOne(new LambdaQueryWrapper<VendingRestock>()
                .eq(VendingRestock::getTenantId, tenantId).eq(VendingRestock::getVendorRestockId, id));
    }

    private VendingFault findFault(Long tenantId, String id) {
        return faultMapper.selectOne(new LambdaQueryWrapper<VendingFault>()
                .eq(VendingFault::getTenantId, tenantId).eq(VendingFault::getVendorFaultId, id));
    }

    private VendingReconciliation findReconciliation(Long tenantId, String id) {
        return reconciliationMapper.selectOne(new LambdaQueryWrapper<VendingReconciliation>()
                .eq(VendingReconciliation::getTenantId, tenantId)
                .eq(VendingReconciliation::getVendorSettlementId, id));
    }

    private <T extends com.zhyq.park.common.base.BaseEntity> Long persist(
            T value, T existing, Writer<T> insert, Writer<T> update, Finder<T> finder) {
        if (existing != null) {
            value.setId(existing.getId());
            value.setVersion(existing.getVersion());
            if (update.write(value) != 1) {
                throw new BizException("售货机记录更新失败，请刷新后重试");
            }
            return value.getId();
        }
        try {
            if (insert.write(value) != 1) {
                throw new BizException("售货机记录写入失败");
            }
            return value.getId();
        } catch (DuplicateKeyException race) {
            T raced = finder.find();
            if (raced == null) {
                throw race;
            }
            value.setId(raced.getId());
            value.setVersion(raced.getVersion());
            if (update.write(value) != 1) {
                throw new BizException("售货机记录并发更新失败");
            }
            return value.getId();
        }
    }

    private ImportBatch requireBatch(Long batchId, String expectedStatus) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getBizType().startsWith("vending_")) {
            throw new BizException("售货机导入批次不存在");
        }
        if (!expectedStatus.equals(batch.getStatus())) {
            throw new BizException("导入批次状态已变化，当前状态: " + batch.getStatus());
        }
        return batch;
    }

    private List<ImportRow> rows(Long batchId) {
        return rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowNo));
    }

    private void refreshBatchCounts(ImportBatch batch, List<ImportRow> rows) {
        int valid = (int) rows.stream().filter(row -> VALID.equals(row.getStatus())).count();
        int invalid = (int) rows.stream().filter(row -> INVALID.equals(row.getStatus())).count();
        batch.setValidRows(valid);
        batch.setInvalidRows(invalid);
        batch.setErrorSummary(invalid == 0 ? null : invalid + " 行存在错误");
        if (batchMapper.updateById(batch) != 1) {
            throw new BizException("导入批次统计更新失败");
        }
    }

    private VendingImportPreview toPreview(ImportBatch batch, List<ImportRow> rows) {
        int valid = (int) rows.stream().filter(row -> VALID.equals(row.getStatus())).count();
        int invalid = (int) rows.stream().filter(row -> INVALID.equals(row.getStatus())).count();
        int excluded = (int) rows.stream().filter(row -> EXCLUDED.equals(row.getStatus())).count();
        List<VendingImportPreview.RowPreview> previews = rows.stream().map(row ->
                new VendingImportPreview.RowPreview(row.getId(), row.getRowNo(), row.getStatus(),
                        row.getErrorMessage(), readValues(row.getNormalizedJson()))).toList();
        return new VendingImportPreview(batch.getId(), batch.getFileName(), batch.getBizType(),
                batch.getStatus(), rows.size(), valid, invalid, excluded, previews);
    }

    private Map<String, String> readValues(String json) {
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (JsonProcessingException e) {
            throw new BizException("导入行数据损坏，无法继续");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("导入审计数据序列化失败");
        }
    }

    private Integer integer(String value) {
        try {
            return new BigDecimal(value.replace(",", "")).intValueExact();
        } catch (Exception e) {
            throw new BizException("导入整数格式错误: " + value);
        }
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (Exception e) {
            throw new BizException("导入金额格式错误: " + value);
        }
    }

    private LocalDateTime dateTimeNullable(String value) {
        return StringUtils.hasText(value) ? dateTime(value) : null;
    }

    private LocalDateTime dateTime(String value) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next standard format.
            }
        }
        throw new BizException("导入时间格式错误: " + value);
    }

    private LocalDate date(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next standard format.
            }
        }
        throw new BizException("导入日期格式错误: " + value);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    @FunctionalInterface
    private interface Writer<T> { int write(T value); }

    @FunctionalInterface
    private interface Finder<T> { T find(); }
}
