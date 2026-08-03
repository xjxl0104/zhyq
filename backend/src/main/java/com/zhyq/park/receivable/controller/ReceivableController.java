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
import com.zhyq.park.importing.entity.ImportRow;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.receivable.dto.ReceivableBindRequest;
import com.zhyq.park.receivable.dto.ReceivableDetail;
import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
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
    public Result<Long> add(@RequestBody ReceivableRegister register) {
        if (!StringUtils.hasText(register.getInternalCode())) {
            register.setInternalCode("RR-M-" + UUID.randomUUID().toString().replace("-", ""));
        }
        if (!StringUtils.hasText(register.getStatus())) register.setStatus("DRAFT");
        if (register.getSourceVersion() == null) register.setSourceVersion(1);
        registerMapper.insert(register);
        return Result.ok(register.getId());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('finance:receivable:edit')")
    @OperationLog(module = "应收明细", action = "修改")
    public Result<Void> update(@RequestBody ReceivableRegister register) {
        registerMapper.updateById(register);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:delete')")
    @OperationLog(module = "应收明细", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        Long bills = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getReceivableRegisterId, id));
        if (bills != null && bills > 0) throw new BizException("已生成账单的应收登记表不能删除");
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

    public record AccountReveal(Long accountId, String accountNo) {}
}
