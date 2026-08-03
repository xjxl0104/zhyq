package com.zhyq.park.receivable.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.importing.entity.ImportBatch;
import com.zhyq.park.importing.dto.ImportBatchSummary;
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.importing.service.ImportFileHasher;
import com.zhyq.park.receivable.dto.ReceivableBindRequest;
import com.zhyq.park.receivable.dto.ReceivableDetail;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import com.zhyq.park.receivable.dto.ReceivableImportPreview;
import com.zhyq.park.receivable.dto.ReceivableUpsertRequest;
import com.zhyq.park.receivable.entity.CollectionAccount;
import com.zhyq.park.receivable.entity.DepositLedger;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.entity.ReceivableRule;
import com.zhyq.park.receivable.mapper.CollectionAccountMapper;
import com.zhyq.park.receivable.mapper.DepositLedgerMapper;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import com.zhyq.park.receivable.service.ReceivableExportService;
import com.zhyq.park.receivable.service.ReceivableImportService;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "财务-应收明细登记表")
@RestController
@RequestMapping("/finance/receivable")
@RequiredArgsConstructor
public class ReceivableController {
    private final ReceivableRegisterMapper registerMapper;
    private final ReceivableRuleMapper ruleMapper;
    private final DepositLedgerMapper depositMapper;
    private final BillMapper billMapper;
    private final CollectionAccountMapper accountMapper;
    private final ReceivableImportService importService;
    private final ReceivablePlanService planService;
    private final ReceivableExportService exportService;
    private final FieldEncryptionService encryptionService;
    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper importRowMapper;

    @GetMapping("/capabilities")
    public Result<ReceivableCapabilities> capabilities() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return Result.ok(new ReceivableCapabilities(
                hasAuthority(authentication, "finance:receivable:query"),
                hasAuthority(authentication, "finance:receivable:add"),
                hasAuthority(authentication, "finance:receivable:edit"),
                hasAuthority(authentication, "finance:receivable:import"),
                hasAuthority(authentication, "finance:receivable:confirm"),
                hasAuthority(authentication, "finance:receivable:generate"),
                hasAuthority(authentication, "finance:receivable:export"),
                hasAuthority(authentication, "finance:receivable:delete"),
                hasAuthority(authentication, "finance:receivable:account:view")));
    }

    @Operation(summary = "分页查询应收明细")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('finance:receivable:query')")
    public Result<PageResult<ReceivableRegister>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String spaceName,
            @RequestParam(required = false) String agreementNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId) {
        LambdaQueryWrapper<ReceivableRegister> query = new LambdaQueryWrapper<ReceivableRegister>()
                .like(StringUtils.hasText(tenantName), ReceivableRegister::getTenantNameRaw, tenantName)
                .like(StringUtils.hasText(spaceName), ReceivableRegister::getSpaceNameRaw, spaceName)
                .like(StringUtils.hasText(agreementNo), ReceivableRegister::getAgreementNoRaw, agreementNo)
                .eq(StringUtils.hasText(status), ReceivableRegister::getStatus, status)
                .eq(contractId != null, ReceivableRegister::getContractId, contractId)
                .orderByAsc(ReceivableRegister::getSeqNo).orderByDesc(ReceivableRegister::getId);
        IPage<ReceivableRegister> page = registerMapper.selectPage(new Page<>(pageNo, pageSize), query);
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "应收明细详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:query')")
    public Result<ReceivableDetail> get(@PathVariable Long id) {
        ReceivableRegister register = registerMapper.selectById(id);
        if (register == null) return Result.ok(null);
        List<ReceivableRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ReceivableRule>()
                .eq(ReceivableRule::getRegisterId, id).orderByAsc(ReceivableRule::getPriority));
        List<DepositLedger> deposits = depositMapper.selectList(new LambdaQueryWrapper<DepositLedger>()
                .eq(DepositLedger::getRegisterId, id));
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getReceivableRegisterId, id).orderByAsc(Bill::getPeriodStart));
        ImportBatch batch = register.getSourceBatchId() == null
                ? null : batchMapper.selectById(register.getSourceBatchId());
        ImportRow sourceRow = register.getSourceRowId() == null
                ? null : importRowMapper.selectById(register.getSourceRowId());
        return Result.ok(new ReceivableDetail(register, rules, deposits, bills, batch, sourceRow));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('finance:receivable:add')")
    @OperationLog(module = "应收明细", action = "新增")
    public Result<Long> add(@RequestBody ReceivableUpsertRequest request) {
        validateEditable(request, false);
        ReceivableRegister register = new ReceivableRegister();
        String manualKey = UUID.randomUUID().toString().replace("-", "");
        register.setInternalCode("RR-M-" + manualKey);
        register.setBusinessKey(ImportFileHasher.sha256(("manual|" + manualKey)
                .getBytes(StandardCharsets.UTF_8)));
        register.setStatus("DRAFT");
        register.setSourceVersion(1);
        register.setChargeArea(BigDecimal.ZERO);
        register.setActualArea(BigDecimal.ZERO);
        register.setSharedArea(BigDecimal.ZERO);
        register.setContractRentTotal(BigDecimal.ZERO);
        register.setRentDeposit(BigDecimal.ZERO);
        register.setPropertyDeposit(BigDecimal.ZERO);
        copyEditable(request, register);
        registerMapper.insert(register);
        return Result.ok(register.getId());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('finance:receivable:edit')")
    @OperationLog(module = "应收明细", action = "修改")
    public Result<Void> update(@RequestBody ReceivableUpsertRequest request) {
        validateEditable(request, true);
        ReceivableRegister register = registerMapper.selectById(request.id());
        if (register == null) throw new BizException("应收登记表不存在");
        if (!List.of("DRAFT", "PENDING_REVIEW").contains(register.getStatus())) {
            throw new BizException("已确认或已生效的应收登记表不能通过普通编辑修改");
        }
        copyEditable(request, register);
        if (registerMapper.updateById(register) != 1) {
            throw new BizException("应收登记表已被修改，请刷新后重试");
        }
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:delete')")
    @OperationLog(module = "应收明细", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        Long bills = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getReceivableRegisterId, id));
        if (bills != null && bills > 0) throw new BizException("已生成账单的应收登记表不能删除");
        ruleMapper.delete(new LambdaQueryWrapper<ReceivableRule>()
                .eq(ReceivableRule::getRegisterId, id));
        depositMapper.delete(new LambdaQueryWrapper<DepositLedger>()
                .eq(DepositLedger::getRegisterId, id));
        registerMapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('finance:receivable:import')")
    @OperationLog(module = "应收明细", action = "导入预览", saveParams = false)
    public Result<ReceivableImportPreview> preview(@RequestPart("file") MultipartFile file) {
        return Result.ok(importService.preview(file));
    }

    @PutMapping("/import/{batchId}/rows/{rowId}/binding")
    @PreAuthorize("hasAuthority('finance:receivable:import')")
    @OperationLog(module = "应收明细", action = "绑定导入主数据")
    public Result<Void> bind(@PathVariable Long batchId, @PathVariable Long rowId,
                             @RequestBody ReceivableBindRequest body) {
        importService.bindRow(batchId, new ReceivableBindRequest(
                rowId, body.tenantRefId(), body.spaceId(), body.roomId(), body.contractId()));
        return Result.ok();
    }

    @PostMapping("/import/{batchId}/confirm")
    @PreAuthorize("hasAuthority('finance:receivable:confirm')")
    @OperationLog(module = "应收明细", action = "确认导入")
    public Result<Integer> confirm(@PathVariable long batchId) {
        return Result.ok(importService.confirm(batchId, username()));
    }

    @PostMapping("/import/{batchId}/rollback")
    @PreAuthorize("hasAuthority('finance:receivable:confirm')")
    @OperationLog(module = "应收明细", action = "撤销导入")
    public Result<Void> rollback(@PathVariable long batchId) {
        importService.rollback(batchId, username());
        return Result.ok();
    }

    @GetMapping("/import/batches")
    @PreAuthorize("hasAuthority('finance:receivable:confirm')")
    public Result<List<ImportBatchSummary>> batches() {
        return Result.ok(batchMapper.selectList(new LambdaQueryWrapper<ImportBatch>()
                        .eq(ImportBatch::getBizType, "rent_receivable")
                        .orderByDesc(ImportBatch::getCreateTime)
                        .orderByDesc(ImportBatch::getId)
                        .last("LIMIT 20"))
                .stream().map(ImportBatchSummary::from).toList());
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('finance:receivable:generate')")
    @OperationLog(module = "应收明细", action = "生成账单")
    public Result<ReceivableGenerateResult> generate(@PathVariable long id) {
        return Result.ok(planService.generate(id));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('finance:receivable:export')")
    @OperationLog(module = "应收明细", action = "导出", saveParams = false)
    public ResponseEntity<byte[]> export() {
        byte[] bytes = exportService.export(registerMapper.selectList(
                new LambdaQueryWrapper<ReceivableRegister>().orderByAsc(ReceivableRegister::getSeqNo)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("应收明细登记表.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/accounts/{id}/reveal")
    @PreAuthorize("hasAuthority('finance:receivable:account:view')")
    @OperationLog(module = "应收明细", action = "查看完整收款账户", saveParams = false)
    public Result<AccountReveal> revealAccount(@PathVariable Long id) {
        CollectionAccount account = accountMapper.selectById(id);
        if (account == null) throw new BizException("收款账户不存在");
        return Result.ok(new AccountReveal(id, encryptionService.decrypt(account.getAccountNoCipher())));
    }

    private static String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private static boolean hasAuthority(org.springframework.security.core.Authentication authentication,
                                        String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private static void validateEditable(ReceivableUpsertRequest request, boolean requireId) {
        if (request == null || (requireId && request.id() == null)) {
            throw new BizException("应收登记参数不完整");
        }
        if (!StringUtils.hasText(request.tenantNameRaw()) || !StringUtils.hasText(request.spaceNameRaw())) {
            throw new BizException("租户和楼层/空间不能为空");
        }
        BigDecimal rent = zero(request.monthlyRent());
        BigDecimal property = zero(request.monthlyProperty());
        if (rent.signum() < 0 || property.signum() < 0 || zero(request.monthlyTotal()).signum() < 0) {
            throw new BizException("月度金额不能为负数");
        }
        if (rent.add(property).compareTo(zero(request.monthlyTotal())) != 0) {
            throw new BizException("月合计必须等于月租金与月物业费之和");
        }
    }

    private static void copyEditable(ReceivableUpsertRequest request, ReceivableRegister target) {
        target.setAgreementNoRaw(request.agreementNoRaw());
        target.setTenantNameRaw(request.tenantNameRaw().trim());
        target.setSpaceNameRaw(request.spaceNameRaw().trim());
        target.setMonthlyRent(zero(request.monthlyRent()));
        target.setMonthlyProperty(zero(request.monthlyProperty()));
        target.setMonthlyTotal(zero(request.monthlyTotal()));
        target.setTenantRefId(request.tenantRefId());
        target.setSpaceId(request.spaceId());
        target.setRoomId(request.roomId());
        target.setContractId(request.contractId());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record AccountReveal(Long accountId, String accountNo) {}
    public record ReceivableCapabilities(boolean query, boolean add, boolean edit,
                                         boolean importData, boolean confirm, boolean generate,
                                         boolean exportData, boolean deleteData, boolean accountView) {}
}
