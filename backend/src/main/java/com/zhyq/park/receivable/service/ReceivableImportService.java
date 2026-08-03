package com.zhyq.park.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportBatchService;
import com.zhyq.park.importing.service.ImportFileHasher;
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
import com.zhyq.park.receivable.model.ReceivableWorkbookData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReceivableImportService {
    private static final String BIZ_TYPE = "rent_receivable";
    private static final String SOURCE_SYSTEM = "excel";
    private static final String ROW_VALID = "VALID";
    private static final String ROW_INVALID = "INVALID";
    private static final String ROW_NEEDS_BINDING = "NEEDS_BINDING";
    private static final String ROW_METADATA = "METADATA";
    private static final String ROW_IMPORTED = "IMPORTED";

    private final ReceivableWorkbookParser workbookParser;
    private final ReceivableRuleParser ruleParser;
    private final ImportBatchService batchService;
    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;
    private final ObjectMapper objectMapper;
    private final ReceivableRegisterMapper registerMapper;
    private final ReceivableRuleMapper ruleMapper;
    private final DepositLedgerMapper depositMapper;
    private final CollectionAccountMapper accountMapper;
    private final BillMapper billMapper;
    private final FieldEncryptionService encryptionService;

    @Transactional(rollbackFor = Exception.class)
    public ReceivableImportPreview preview(MultipartFile file) {
        return preview(file, 1L);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivableImportPreview preview(MultipartFile file, Long tenantId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择应收明细工作簿");
        }
        String originalName = StringUtils.cleanPath(
                Optional.ofNullable(file.getOriginalFilename()).orElse("receivable.xlsx"));
        String extension = FileStorageService.extOf(originalName);
        if (!"xlsx".equals(extension) && !"xls".equals(extension)) {
            throw new BizException("应收明细仅支持 .xlsx 或 .xls 文件");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("无法读取上传文件");
        }
        ReceivableWorkbookData data = workbookParser.parse(bytes);
        List<String> totalErrors = validateTotals(data);
        ImportBatch batch = batchService.createBatch(
                BIZ_TYPE, SOURCE_SYSTEM, originalName, bytes, null, tenantId);

        // The source workbook can contain bank account numbers. Keep only the
        // scrubbed audit rows below and encrypted account payloads; never place
        // the original bytes in the generic attachment store.
        List<ImportRow> importRows = new ArrayList<>();
        for (ReceivableWorkbookData.RowData row : data.rows()) {
            importRows.add(toImportRow(batch, data.sheetName(), row, totalErrors));
        }
        importRows.add(toTotalsRow(batch, data));
        batchService.saveRows(batch.getId(), importRows);

        int valid = (int) importRows.stream().filter(row -> ROW_VALID.equals(row.getStatus())).count();
        int invalid = data.rows().size() - valid;
        String summary = invalid == 0 ? null : invalid + " 行待校验或主数据绑定";
        batchService.markPendingConfirm(batch.getId(), data.rows().size(), valid, invalid, summary);

        List<ReceivableImportPreview.RowPreview> previews = importRows.stream()
                .filter(row -> !ROW_METADATA.equals(row.getStatus()))
                .map(this::toRowPreview)
                .toList();
        return new ReceivableImportPreview(
                batch.getId(), originalName, ImportBatchService.PENDING_CONFIRM,
                data.rows().size(), valid, invalid, data.totals(), previews);
    }

    @Transactional
    public void bindRow(Long batchId, ReceivableBindRequest request) {
        ImportBatch batch = requireBatch(batchId, ImportBatchService.PENDING_CONFIRM);
        if (request == null || request.rowId() == null) {
            throw new BizException("请选择要绑定的导入行");
        }
        ImportRow row = rowMapper.selectById(request.rowId());
        if (row == null || !batchId.equals(row.getBatchId()) || ROW_METADATA.equals(row.getStatus())) {
            throw new BizException("导入行不存在或不属于该批次");
        }

        ObjectNode normalized = readObject(row.getNormalizedJson());
        ObjectNode binding = normalized.with("binding");
        putNullable(binding, "tenantRefId", request.tenantRefId());
        putNullable(binding, "spaceId", request.spaceId());
        putNullable(binding, "roomId", request.roomId());
        putNullable(binding, "contractId", request.contractId());

        List<String> errors = jsonStrings(normalized.path("validationErrors"));
        boolean bindingComplete = request.tenantRefId() != null
                && (request.spaceId() != null || request.roomId() != null)
                && request.contractId() != null;
        row.setStatus(!errors.isEmpty() ? ROW_INVALID : bindingComplete ? ROW_VALID : ROW_NEEDS_BINDING);
        row.setErrorMessage(!errors.isEmpty()
                ? String.join("；", errors)
                : bindingComplete ? null : "请绑定租户、空间/房间及合同");
        row.setNormalizedJson(write(normalized));
        if (rowMapper.updateById(row) != 1) {
            throw new BizException("导入行绑定失败，请刷新后重试");
        }
        refreshBatchCounts(batch);
    }

    @Transactional(rollbackFor = Exception.class)
    public int confirm(Long batchId, String confirmedBy) {
        ImportBatch batch = requireBatch(batchId, ImportBatchService.PENDING_CONFIRM);
        List<ImportRow> rows = rows(batchId).stream()
                .filter(row -> !ROW_METADATA.equals(row.getStatus()))
                .toList();
        long validRows = rows.stream().filter(row -> ROW_VALID.equals(row.getStatus())).count();
        if (rows.size() != batch.getTotalRows() || validRows != rows.size()
                || batch.getInvalidRows() == null || batch.getInvalidRows() != 0) {
            throw new BizException("批次仍有错误或未绑定行，不能确认");
        }

        Map<String, Long> accountCache = new HashMap<>();
        int imported = 0;
        for (ImportRow importRow : rows) {
            ObjectNode normalized = readObject(importRow.getNormalizedJson());
            ReceivableWorkbookData.RowData rowData = treeRowData(normalized.path("rowData"));
            JsonNode binding = normalized.path("binding");
            Long tenantRefId = requiredLong(binding, "tenantRefId");
            Long spaceId = nullableLong(binding, "spaceId");
            Long roomId = nullableLong(binding, "roomId");
            Long contractId = nullableLong(binding, "contractId");
            if (spaceId == null && roomId == null) {
                throw new BizException("第" + importRow.getRowNo() + "行未绑定空间/房间");
            }

            String rentAccountRaw = decryptNullable(normalized.path("rentAccountCipher").asText(null));
            String propertyAccountRaw = decryptNullable(normalized.path("propertyAccountCipher").asText(null));
            Long rentAccountId = persistAccount(batch.getTenantId(), "RENT", rentAccountRaw, accountCache);
            Long propertyAccountId = persistAccount(batch.getTenantId(), "PROPERTY", propertyAccountRaw, accountCache);

            ReceivableRegister incoming = toRegister(
                    batch, importRow, rowData, tenantRefId, spaceId, roomId, contractId,
                    rentAccountId, propertyAccountId,
                    normalized.path("rentAccountMasked").asText(null),
                    normalized.path("propertyAccountMasked").asText(null));
            ReceivableRegister register = registerMapper.selectOne(
                    new LambdaQueryWrapper<ReceivableRegister>()
                            .eq(ReceivableRegister::getTenantId, batch.getTenantId())
                            .eq(ReceivableRegister::getBusinessKey, importRow.getRowFingerprint()));
            if (register == null) {
                register = incoming;
                registerMapper.insert(register);
            } else {
                rejectCorrectionWithDownstreamData(register);
                snapshotPreviousState(normalized, register);
                int nextSourceVersion = Optional.ofNullable(register.getSourceVersion()).orElse(1) + 1;
                BeanUtils.copyProperties(incoming, register,
                        "id", "tenantId", "internalCode", "businessKey", "sourceVersion",
                        "createBy", "createTime", "version", "deleted");
                register.setSourceVersion(nextSourceVersion);
                if (registerMapper.updateById(register) != 1) {
                    throw new BizException("应收登记表修订冲突，请刷新后重试");
                }
                ruleMapper.delete(new LambdaQueryWrapper<ReceivableRule>()
                        .eq(ReceivableRule::getRegisterId, register.getId()));
                depositMapper.delete(new LambdaQueryWrapper<DepositLedger>()
                        .eq(DepositLedger::getRegisterId, register.getId()));
                importRow.setNormalizedJson(write(normalized));
            }
            persistRules(register, rowData);
            persistDeposits(register, rowData);

            importRow.setStatus(ROW_IMPORTED);
            importRow.setTargetType("RECEIVABLE_REGISTER");
            importRow.setTargetId(register.getId());
            importRow.setErrorMessage(null);
            rowMapper.updateById(importRow);
            imported++;
        }
        batchService.markCompleted(batchId, imported, confirmedBy);
        return imported;
    }

    @Transactional
    public void rollback(Long batchId, String rollbackBy) {
        requireBatch(batchId, ImportBatchService.COMPLETED);
        Long downstreamBills = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .inSql(Bill::getReceivableRegisterId,
                        "SELECT id FROM fin_receivable_register WHERE source_batch_id=" + batchId + " AND deleted=0"));
        if (downstreamBills != null && downstreamBills > 0) {
            throw new BizException("该批次已生成账单或财务后续数据，不能整批撤销");
        }

        for (ImportRow row : rows(batchId)) {
            if (!ROW_IMPORTED.equals(row.getStatus()) || row.getTargetId() == null) continue;
            ReceivableRegister register = registerMapper.selectById(row.getTargetId());
            if (register == null) continue;
            ruleMapper.delete(new LambdaQueryWrapper<ReceivableRule>()
                    .eq(ReceivableRule::getRegisterId, register.getId()));
            depositMapper.delete(new LambdaQueryWrapper<DepositLedger>()
                    .eq(DepositLedger::getRegisterId, register.getId()));
            ObjectNode normalized = readObject(row.getNormalizedJson());
            JsonNode previousNode = normalized.get("previousRegister");
            if (previousNode != null && !previousNode.isNull()) {
                ReceivableRegister previous = treeValue(previousNode, ReceivableRegister.class);
                previous.setId(register.getId());
                if (registerMapper.updateById(previous) != 1) {
                    throw new BizException("应收登记表修订撤销冲突，请刷新后重试");
                }
                restoreRules(normalized.path("previousRules"), previous.getId());
                restoreDeposits(normalized.path("previousDeposits"), previous.getId());
            } else {
                registerMapper.deleteById(register.getId());
            }
            row.setStatus(ImportBatchService.ROLLED_BACK);
            rowMapper.updateById(row);
        }
        batchService.markRolledBack(batchId, rollbackBy);
    }

    private ImportRow toImportRow(ImportBatch batch, String sheetName,
                                  ReceivableWorkbookData.RowData row, List<String> totalErrors) {
        List<String> validationErrors = new ArrayList<>(totalErrors);
        validateRow(row, validationErrors);

        String rentCipher = encryptNullable(row.rentAccountRaw());
        String propertyCipher = encryptNullable(row.propertyAccountRaw());
        String rentMasked = maskAccountRaw(row.rentAccountRaw());
        String propertyMasked = maskAccountRaw(row.propertyAccountRaw());

        ObjectNode rowData = objectMapper.valueToTree(row);
        scrubAccountFields(rowData, rentMasked, propertyMasked);
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("rowData", rowData);
        normalized.put("rentAccountCipher", rentCipher);
        normalized.put("propertyAccountCipher", propertyCipher);
        normalized.put("rentAccountMasked", rentMasked);
        normalized.put("propertyAccountMasked", propertyMasked);
        normalized.set("binding", objectMapper.createObjectNode());
        ArrayNode errors = normalized.putArray("validationErrors");
        validationErrors.forEach(errors::add);

        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("sourceRow", row.sourceRow());
        ObjectNode cells = objectMapper.valueToTree(row.rawValues());
        cells.put("rentAccount", rentMasked);
        cells.put("propertyAccount", propertyMasked);
        raw.set("cells", cells);
        raw.set("formulas", objectMapper.valueToTree(row.formulas()));

        ImportRow importRow = new ImportRow();
        importRow.setTenantId(batch.getTenantId());
        importRow.setSheetName(sheetName);
        importRow.setRowNo(row.sourceRow());
        importRow.setRowFingerprint(ImportFileHasher.rowFingerprint(BIZ_TYPE, List.of(
                nullToEmpty(row.agreementNoRaw()), nullToEmpty(row.tenantNameRaw()),
                nullToEmpty(row.spaceNameRaw()), nullToEmpty(row.contractPeriodRaw()))));
        importRow.setRawJson(write(raw));
        importRow.setNormalizedJson(write(normalized));
        importRow.setStatus(validationErrors.isEmpty() ? ROW_NEEDS_BINDING : ROW_INVALID);
        importRow.setErrorMessage(validationErrors.isEmpty()
                ? "请绑定租户、空间/房间及合同" : String.join("；", validationErrors));
        return importRow;
    }

    private ImportRow toTotalsRow(ImportBatch batch, ReceivableWorkbookData data) {
        ImportRow row = new ImportRow();
        row.setTenantId(batch.getTenantId());
        row.setSheetName(data.sheetName());
        row.setRowNo(data.headerRow() + data.rows().size() + 1);
        row.setRowFingerprint(ImportFileHasher.rowFingerprint(BIZ_TYPE + "_totals", List.of(
                value(data.totals().chargeArea()), value(data.totals().contractRentTotal()),
                value(data.totals().monthlyTotal()))));
        row.setRawJson(write(objectMapper.valueToTree(data.totals())));
        row.setNormalizedJson(row.getRawJson());
        row.setStatus(ROW_METADATA);
        row.setTargetType("RECEIVABLE_TOTALS");
        return row;
    }

    private List<String> validateTotals(ReceivableWorkbookData data) {
        List<String> errors = new ArrayList<>();
        compareTotal(errors, "计租总面积", sum(data.rows(), ReceivableWorkbookData.RowData::chargeArea), data.totals().chargeArea());
        compareTotal(errors, "实际房产面积", sum(data.rows(), ReceivableWorkbookData.RowData::actualArea), data.totals().actualArea());
        compareTotal(errors, "分摊面积", sum(data.rows(), ReceivableWorkbookData.RowData::sharedArea), data.totals().sharedArea());
        compareTotal(errors, "合同租金总金额", sum(data.rows(), ReceivableWorkbookData.RowData::contractRentTotal), data.totals().contractRentTotal());
        compareTotal(errors, "月租金", sum(data.rows(), ReceivableWorkbookData.RowData::monthlyRent), data.totals().monthlyRent());
        compareTotal(errors, "月物业费", sum(data.rows(), ReceivableWorkbookData.RowData::monthlyProperty), data.totals().monthlyProperty());
        compareTotal(errors, "月合计", sum(data.rows(), ReceivableWorkbookData.RowData::monthlyTotal), data.totals().monthlyTotal());
        compareTotal(errors, "租金保证金", sum(data.rows(), ReceivableWorkbookData.RowData::rentDeposit), data.totals().rentDeposit());
        compareTotal(errors, "物业保证金", sum(data.rows(), ReceivableWorkbookData.RowData::propertyDeposit), data.totals().propertyDeposit());
        return errors;
    }

    private void validateRow(ReceivableWorkbookData.RowData row, List<String> errors) {
        if (!StringUtils.hasText(row.tenantNameRaw())) errors.add("租户不能为空");
        if (!StringUtils.hasText(row.spaceNameRaw())) errors.add("楼层/空间不能为空");
        if (!equal(row.chargeArea(), add(row.actualArea(), row.sharedArea()))) errors.add("计租面积不等于实际面积与分摊面积之和");
        if (!equal(row.monthlyTotal(), add(row.monthlyRent(), row.monthlyProperty()))) errors.add("月合计不等于月租金与月物业费之和");
        if (StringUtils.hasText(row.rentAccountRaw()) && ruleParser.parseAccount(row.rentAccountRaw()).isEmpty()) errors.add("租金收款账户格式无法识别");
        if (StringUtils.hasText(row.propertyAccountRaw()) && ruleParser.parseAccount(row.propertyAccountRaw()).isEmpty()) errors.add("物业收款账户格式无法识别");
    }

    private ReceivableRegister toRegister(ImportBatch batch, ImportRow source,
                                          ReceivableWorkbookData.RowData row, Long tenantRefId,
                                          Long spaceId, Long roomId, Long contractId,
                                          Long rentAccountId, Long propertyAccountId,
                                          String rentAccountMasked, String propertyAccountMasked) {
        ReceivableRegister register = new ReceivableRegister();
        register.setTenantId(batch.getTenantId());
        register.setBusinessKey(source.getRowFingerprint());
        register.setInternalCode("RR-" + source.getRowFingerprint().substring(0, 20));
        register.setSeqNo(row.seqNo());
        register.setAgreementNoRaw(row.agreementNoRaw());
        register.setTenantNameRaw(row.tenantNameRaw());
        register.setSpaceNameRaw(row.spaceNameRaw());
        register.setChargeArea(row.chargeArea());
        register.setActualArea(row.actualArea());
        register.setSharedArea(row.sharedArea());
        register.setContractTermRaw(row.contractTermRaw());
        register.setContractRentTotal(row.contractRentTotal());
        register.setContractPeriodRaw(row.contractPeriodRaw());
        ruleParser.parseContractTerm(row.contractPeriodRaw()).ifPresent(range -> {
            register.setContractStartDate(range.start());
            register.setContractEndDate(range.end());
        });
        register.setEscalationRaw(row.escalationRaw());
        register.setFreeTermRaw(row.freeTermRaw());
        register.setFreePeriodRaw(row.freePeriodRaw());
        register.setDiscountRaw(row.discountRaw());
        register.setRentRateRaw(row.rentRateRaw());
        register.setPropertyRateRaw(row.propertyRateRaw());
        register.setMonthlyRent(row.monthlyRent());
        register.setMonthlyProperty(row.monthlyProperty());
        register.setMonthlyTotal(row.monthlyTotal());
        register.setRentDeposit(row.rentDeposit());
        register.setPropertyDeposit(row.propertyDeposit());
        register.setCollectionTimingRaw(row.collectionTimingRaw());
        register.setFirstCollectionRaw(row.firstCollectionRaw());
        register.setRentAccountId(rentAccountId);
        register.setPropertyAccountId(propertyAccountId);
        register.setRentAccountMasked(rentAccountMasked);
        register.setPropertyAccountMasked(propertyAccountMasked);
        register.setNotesRaw(row.notesRaw());
        register.setDepositDifference(row.depositDifference());
        register.setTenantRefId(tenantRefId);
        register.setSpaceId(spaceId);
        register.setRoomId(roomId);
        register.setContractId(contractId);
        register.setStatus("CONFIRMED");
        register.setSourceBatchId(batch.getId());
        register.setSourceRowId(source.getId());
        register.setSourceVersion(1);
        return register;
    }

    private void rejectCorrectionWithDownstreamData(ReceivableRegister register) {
        Long bills = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getReceivableRegisterId, register.getId()));
        if (bills != null && bills > 0) {
            throw new BizException("应收登记表已生成账单，不能用修订工作簿直接覆盖");
        }
        List<DepositLedger> deposits = depositMapper.selectList(new LambdaQueryWrapper<DepositLedger>()
                .eq(DepositLedger::getRegisterId, register.getId()));
        boolean received = deposits.stream().anyMatch(deposit -> deposit.getConfirmedReceivedAmount() != null
                && deposit.getConfirmedReceivedAmount().signum() > 0);
        if (received) throw new BizException("应收登记表已有保证金实收，不能用修订工作簿直接覆盖");
    }

    private void snapshotPreviousState(ObjectNode normalized, ReceivableRegister register) {
        normalized.set("previousRegister", objectMapper.valueToTree(register));
        normalized.set("previousRules", objectMapper.valueToTree(ruleMapper.selectList(
                new LambdaQueryWrapper<ReceivableRule>().eq(ReceivableRule::getRegisterId, register.getId()))));
        normalized.set("previousDeposits", objectMapper.valueToTree(depositMapper.selectList(
                new LambdaQueryWrapper<DepositLedger>().eq(DepositLedger::getRegisterId, register.getId()))));
    }

    private void restoreRules(JsonNode node, Long registerId) {
        if (!node.isArray()) return;
        for (JsonNode item : node) {
            ReceivableRule rule = treeValue(item, ReceivableRule.class);
            clearIdentity(rule);
            rule.setRegisterId(registerId);
            ruleMapper.insert(rule);
        }
    }

    private void restoreDeposits(JsonNode node, Long registerId) {
        if (!node.isArray()) return;
        for (JsonNode item : node) {
            DepositLedger deposit = treeValue(item, DepositLedger.class);
            clearIdentity(deposit);
            deposit.setRegisterId(registerId);
            depositMapper.insert(deposit);
        }
    }

    private static void clearIdentity(com.zhyq.park.common.base.BaseEntity entity) {
        entity.setId(null);
        entity.setVersion(null);
        entity.setDeleted(null);
        entity.setCreateTime(null);
        entity.setUpdateTime(null);
    }

    private void persistRules(ReceivableRegister register, ReceivableWorkbookData.RowData row) {
        persistBaseRules(register, "RENT", row.rentRateRaw(), row.monthlyRent());
        persistBaseRules(register, "PROPERTY", row.propertyRateRaw(), row.monthlyProperty());
        ruleParser.parseEscalation(row.escalationRaw()).ifPresent(escalation -> {
            persistEscalation(register, "RENT", escalation, row.escalationRaw());
            persistEscalation(register, "PROPERTY", escalation, row.escalationRaw());
        });
        persistActionableConditions(register, row);
    }

    private void persistActionableConditions(ReceivableRegister register,
                                             ReceivableWorkbookData.RowData row) {
        boolean waivePropertyDuringFreePeriod = containsAny(row.discountRaw(),
                "免物业", "无需支付物业", "免缴物业", "物业管理费按0");
        for (ReceivableRuleParser.DateRange range : ruleParser.parseDateRanges(row.freePeriodRaw())) {
            persistWaiver(register, "RENT", range, row.freePeriodRaw());
            if (waivePropertyDuringFreePeriod) {
                persistWaiver(register, "PROPERTY", range, row.discountRaw());
            }
        }

        String combined = nullToEmpty(row.freePeriodRaw()) + "；" + nullToEmpty(row.discountRaw());
        if (ruleParser.isYearlyLastMonthWaiver(combined)) {
            ReceivableRule recurring = baseRule(register, "RENT", "RECURRING_WAIVER", 40);
            recurring.setEffectiveStart(register.getContractStartDate());
            recurring.setEffectiveEnd(register.getContractEndDate());
            recurring.setRecurrenceRule("YEARLY_LAST_MONTH");
            recurring.setRawText(combined);
            ruleMapper.insert(recurring);
        }

        if (!StringUtils.hasText(row.discountRaw())) return;
        for (String clause : row.discountRaw().split("[；;\\n]+")) {
            List<ReceivableRuleParser.DateRange> ranges = ruleParser.parseDateRanges(clause);
            if (ranges.isEmpty()) continue;

            ruleParser.parseDiscountRate(clause).ifPresent(rate -> {
                if (mentionsRent(clause) || !mentionsProperty(clause)) {
                    ranges.forEach(range -> persistDiscount(register, "RENT", range, rate, clause));
                }
                if (mentionsProperty(clause)) {
                    ranges.forEach(range -> persistDiscount(register, "PROPERTY", range, rate, clause));
                }
            });

            boolean rentWaiver = containsAny(clause, "抵扣") && mentionsRent(clause)
                    || containsAny(clause, "免租期", "免租金", "无需支付租赁费");
            boolean propertyWaiver = mentionsProperty(clause)
                    && containsAny(clause, "免缴", "无需支付", "免物业", "免管理费");
            if (rentWaiver) ranges.forEach(range -> persistWaiver(register, "RENT", range, clause));
            if (propertyWaiver) ranges.forEach(range -> persistWaiver(register, "PROPERTY", range, clause));
        }
    }

    private void persistWaiver(ReceivableRegister register, String feeType,
                               ReceivableRuleParser.DateRange range, String raw) {
        ReceivableRule waiver = baseRule(register, feeType, "WAIVER", 40);
        waiver.setEffectiveStart(range.start());
        waiver.setEffectiveEnd(range.end());
        waiver.setRawText(raw);
        ruleMapper.insert(waiver);
    }

    private void persistDiscount(ReceivableRegister register, String feeType,
                                 ReceivableRuleParser.DateRange range, BigDecimal rate, String raw) {
        ReceivableRule discount = baseRule(register, feeType, "DISCOUNT", 45);
        discount.setEffectiveStart(range.start());
        discount.setEffectiveEnd(range.end());
        discount.setDiscountRate(rate);
        discount.setRawText(raw);
        ruleMapper.insert(discount);
    }

    private static boolean mentionsRent(String raw) {
        return containsAny(raw, "租金", "租赁费", "免租");
    }

    private static boolean mentionsProperty(String raw) {
        return containsAny(raw, "物业", "管理费");
    }

    private static boolean containsAny(String raw, String... tokens) {
        if (!StringUtils.hasText(raw)) return false;
        for (String token : tokens) if (raw.contains(token)) return true;
        return false;
    }

    private void persistBaseRules(ReceivableRegister register, String feeType,
                                  String rawRate, BigDecimal authoritativeAmount) {
        ruleParser.parseRate(rawRate).ifPresent(rate -> {
            ReceivableRule rule = baseRule(register, feeType, "BASE_RATE", 20);
            rule.setRateUnit(rate.unit());
            rule.setRateValue(rate.value());
            rule.setRawText(rawRate);
            ruleMapper.insert(rule);
        });
        ReceivableRule authoritative = baseRule(register, feeType, "AUTHORITATIVE_MONTHLY", 10);
        authoritative.setFixedAmount(zero(authoritativeAmount));
        authoritative.setRawText("Excel权威月度金额");
        ruleMapper.insert(authoritative);
    }

    private void persistEscalation(ReceivableRegister register, String feeType,
                                   ReceivableRuleParser.Escalation escalation, String raw) {
        ReceivableRule rule = baseRule(register, feeType, "ESCALATION", 30);
        rule.setIntervalYears(escalation.intervalYears());
        rule.setIncreaseRate(escalation.increasePercent());
        rule.setRawText(raw);
        ruleMapper.insert(rule);
    }

    private ReceivableRule baseRule(ReceivableRegister register, String feeType,
                                    String ruleType, int priority) {
        ReceivableRule rule = new ReceivableRule();
        rule.setTenantId(register.getTenantId());
        rule.setRegisterId(register.getId());
        rule.setFeeType(feeType);
        rule.setRuleType(ruleType);
        rule.setPriority(priority);
        rule.setStatus("ACTIVE");
        return rule;
    }

    private void persistDeposits(ReceivableRegister register, ReceivableWorkbookData.RowData row) {
        persistDeposit(register, "RENT_DEPOSIT", row.rentDeposit(), row.depositDifference());
        persistDeposit(register, "PROPERTY_DEPOSIT", row.propertyDeposit(), null);
    }

    private void persistDeposit(ReceivableRegister register, String type,
                                BigDecimal required, BigDecimal sourceDifference) {
        DepositLedger ledger = new DepositLedger();
        ledger.setTenantId(register.getTenantId());
        ledger.setRegisterId(register.getId());
        ledger.setDepositType(type);
        ledger.setRequiredAmount(zero(required));
        ledger.setConfirmedReceivedAmount(BigDecimal.ZERO.setScale(2));
        ledger.setDifferenceAmount(zero(required));
        ledger.setSourceDifferenceAmount(sourceDifference);
        ledger.setStatus(zero(required).signum() == 0 ? "NOT_REQUIRED" : "PENDING");
        depositMapper.insert(ledger);
    }

    private Long persistAccount(Long tenantId, String accountType, String raw,
                                Map<String, Long> accountCache) {
        if (!StringUtils.hasText(raw)) return null;
        ReceivableRuleParser.Account parsed = ruleParser.parseAccount(raw)
                .orElseThrow(() -> new BizException(accountType + "收款账户格式无法识别"));
        String fingerprint = ImportFileHasher.sha256(
                (tenantId + "|" + parsed.accountNo()).getBytes(StandardCharsets.UTF_8));
        Long cached = accountCache.get(fingerprint);
        if (cached != null) return cached;

        CollectionAccount existing = accountMapper.selectOne(new LambdaQueryWrapper<CollectionAccount>()
                .eq(CollectionAccount::getTenantId, tenantId)
                .eq(CollectionAccount::getAccountFingerprint, fingerprint));
        if (existing != null) {
            accountCache.put(fingerprint, existing.getId());
            return existing.getId();
        }
        CollectionAccount account = new CollectionAccount();
        account.setTenantId(tenantId);
        account.setAccountType(accountType);
        account.setAccountName(parsed.accountName());
        account.setBankName(parsed.bankName());
        account.setAccountNoCipher(encryptionService.encrypt(parsed.accountNo()));
        account.setAccountNoMasked(encryptionService.mask(parsed.accountNo()));
        account.setAccountFingerprint(fingerprint);
        account.setStatus("ACTIVE");
        accountMapper.insert(account);
        accountCache.put(fingerprint, account.getId());
        return account.getId();
    }

    private ReceivableImportPreview.RowPreview toRowPreview(ImportRow row) {
        ObjectNode normalized = readObject(row.getNormalizedJson());
        JsonNode data = normalized.path("rowData");
        return new ReceivableImportPreview.RowPreview(
                row.getId(), row.getRowNo(), nullableInt(data, "seqNo"),
                data.path("agreementNoRaw").asText(null), data.path("tenantNameRaw").asText(null),
                data.path("spaceNameRaw").asText(null), row.getStatus(), row.getErrorMessage(),
                normalized.path("rentAccountMasked").asText(null),
                normalized.path("propertyAccountMasked").asText(null));
    }

    private void refreshBatchCounts(ImportBatch batch) {
        List<ImportRow> businessRows = rows(batch.getId()).stream()
                .filter(row -> !ROW_METADATA.equals(row.getStatus())).toList();
        int valid = (int) businessRows.stream().filter(row -> ROW_VALID.equals(row.getStatus())).count();
        int invalid = businessRows.size() - valid;
        batch.setValidRows(valid);
        batch.setInvalidRows(invalid);
        batch.setErrorSummary(invalid == 0 ? null : invalid + " 行待校验或主数据绑定");
        if (batchMapper.updateById(batch) != 1) {
            throw new BizException("导入批次统计更新失败");
        }
    }

    private List<ImportRow> rows(Long batchId) {
        return rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getBatchId, batchId).orderByAsc(ImportRow::getRowNo));
    }

    private ImportBatch requireBatch(Long batchId, String status) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("导入批次不存在");
        if (!status.equals(batch.getStatus())) throw new BizException("导入批次当前状态不可执行该操作");
        return batch;
    }

    private void scrubAccountFields(ObjectNode rowData, String rentMasked, String propertyMasked) {
        rowData.put("rentAccountRaw", rentMasked);
        rowData.put("propertyAccountRaw", propertyMasked);
        JsonNode rawValues = rowData.path("rawValues");
        if (rawValues instanceof ObjectNode values) {
            values.put("rentAccount", rentMasked);
            values.put("propertyAccount", propertyMasked);
        }
    }

    private String maskAccountRaw(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return ruleParser.parseAccount(raw)
                .map(account -> raw.replace(account.accountNo(), encryptionService.mask(account.accountNo())))
                .orElseGet(() -> raw.replaceAll("\\d{8,}", "****"));
    }

    private String encryptNullable(String raw) {
        return StringUtils.hasText(raw) ? encryptionService.encrypt(raw) : null;
    }

    private String decryptNullable(String cipher) {
        return StringUtils.hasText(cipher) ? encryptionService.decrypt(cipher) : null;
    }

    private ReceivableWorkbookData.RowData treeRowData(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, ReceivableWorkbookData.RowData.class);
        } catch (JsonProcessingException e) {
            throw new BizException("导入行结构已损坏");
        }
    }

    private <T> T treeValue(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new BizException("导入修订快照已损坏");
        }
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode object) return object;
            throw new BizException("导入行结构不正确");
        } catch (JsonProcessingException e) {
            throw new BizException("导入行 JSON 无法读取");
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BizException("导入审计数据序列化失败");
        }
    }

    private static void putNullable(ObjectNode node, String field, Long value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static Long requiredLong(JsonNode node, String field) {
        Long value = nullableLong(node, field);
        if (value == null) throw new BizException("导入行缺少" + field + "绑定");
        return value;
    }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static List<String> jsonStrings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static BigDecimal sum(List<ReceivableWorkbookData.RowData> rows,
                                  java.util.function.Function<ReceivableWorkbookData.RowData, BigDecimal> getter) {
        return rows.stream().map(getter).map(ReceivableImportService::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void compareTotal(List<String> errors, String label, BigDecimal actual, BigDecimal expected) {
        if (!equal(actual, expected)) errors.add(label + "总计不一致");
    }

    private static boolean equal(BigDecimal left, BigDecimal right) {
        return zero(left).compareTo(zero(right)) == 0;
    }

    private static BigDecimal add(BigDecimal first, BigDecimal second) {
        return zero(first).add(zero(second));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String value(BigDecimal value) {
        return zero(value).toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
