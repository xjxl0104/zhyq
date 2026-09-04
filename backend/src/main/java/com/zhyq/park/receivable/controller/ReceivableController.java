package com.zhyq.park.receivable.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.LateFeeService;
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
import com.zhyq.park.receivable.dto.ReceivableProvisionPreview;
import com.zhyq.park.receivable.dto.ReceivableProvisionRequest;
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
import com.zhyq.park.receivable.service.ReceivableAutoBillService;
import com.zhyq.park.receivable.service.ReceivableExportService;
import com.zhyq.park.receivable.service.ReceivableImportService;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.receivable.service.ReceivableProvisionService;
import com.zhyq.park.receivable.service.ReceivableBindingValidator;
import com.zhyq.park.receivable.service.ReceivableCalculator;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ReceivableAutoBillService autoBillService;
    private final ReceivableProvisionService provisionService;
    private final ReceivableExportService exportService;
    private final FieldEncryptionService encryptionService;
    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper importRowMapper;
    private final ReceivableCalculator calculator;
    private final LateFeeService lateFeeService;
    private final ReceivableBindingValidator bindingValidator;

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
        fillBillCounts(page.getRecords());
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /**
     * 给每条登记填上已生成账单数。登记表是账单的源头,列表上必须看得见
     * "生成到哪了"——否则联动是黑箱,用户只能挨条点详情确认。
     */
    private void fillBillCounts(List<ReceivableRegister> registers) {
        if (registers == null || registers.isEmpty()) {
            return;
        }
        List<Long> ids = registers.stream().map(ReceivableRegister::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        billMapper.selectMaps(new QueryWrapper<Bill>()
                        .select("receivable_register_id AS rid", "COUNT(*) AS cnt")
                        .in("receivable_register_id", ids)
                        .groupBy("receivable_register_id"))
                .forEach(row -> counts.put(((Number) row.get("rid")).longValue(),
                        ((Number) row.get("cnt")).intValue()));
        registers.forEach(r -> r.setBillCount(counts.getOrDefault(r.getId(), 0)));
    }

    @Operation(summary = "月度应收汇总（按月计算每条登记表的实际应收金额，含递增/减免/折扣）")
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAuthority('finance:receivable:query')")
    public Result<MonthlySummaryResponse> monthlySummary(
            @RequestParam String month,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String status) {
        YearMonth period = YearMonth.parse(month);
        LambdaQueryWrapper<ReceivableRegister> qw = new LambdaQueryWrapper<ReceivableRegister>()
                .like(StringUtils.hasText(tenantName), ReceivableRegister::getTenantNameRaw, tenantName)
                .eq(StringUtils.hasText(status), ReceivableRegister::getStatus, status)
                .and(w -> w
                        .le(ReceivableRegister::getContractStartDate, period.atEndOfMonth())
                        .ge(ReceivableRegister::getContractEndDate, period.atDay(1)))
                .orderByAsc(ReceivableRegister::getSeqNo);
        List<ReceivableRegister> registers = registerMapper.selectList(qw);

        BigDecimal totalRent = BigDecimal.ZERO;
        BigDecimal totalProperty = BigDecimal.ZERO;
        List<MonthlyLineItem> items = new java.util.ArrayList<>();

        for (ReceivableRegister reg : registers) {
            List<ReceivableRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ReceivableRule>()
                    .eq(ReceivableRule::getRegisterId, reg.getId())
                    .eq(ReceivableRule::getStatus, "ACTIVE"));
            BigDecimal rent = calculator.amountForMonth(reg, rules, "RENT", period);
            BigDecimal property = calculator.amountForMonth(reg, rules, "PROPERTY", period);
            BigDecimal total = rent.add(property);
            totalRent = totalRent.add(rent);
            totalProperty = totalProperty.add(property);
            items.add(new MonthlyLineItem(
                    reg.getId(), reg.getTenantNameRaw(), reg.getSpaceNameRaw(),
                    reg.getAgreementNoRaw(), reg.getStatus(),
                    rent, property, total,
                    reg.getContractStartDate(), reg.getContractEndDate(),
                    reg.getFreePeriodRaw(), reg.getDiscountRaw(),
                    reg.getCollectionTimingRaw(), calculator.dueDate(reg, period)));
        }
        return Result.ok(new MonthlySummaryResponse(
                month, items.size(), totalRent, totalProperty, totalRent.add(totalProperty), items));
    }

    @Operation(summary = "应收明细详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:query')")
    public Result<ReceivableDetail> get(@PathVariable Long id) {
        ReceivableRegister register = registerMapper.selectById(id);
        if (register == null) return Result.ok(null);
        List<ReceivableRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ReceivableRule>()
                .eq(ReceivableRule::getRegisterId, id).orderByAsc(ReceivableRule::getPriority));
        // 历史导入批次没落过规则行,计费规则页签不能空着:按与出账完全相同的
        // 推断逻辑展示(首行=按月计费,每月每客户一张租金+一张物业费账单)
        if (rules.isEmpty()) {
            rules = calculator.displayRules(register);
        }
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
        validateBinding(request, MyMetaObjectHandler.DEFAULT_TENANT_ID);
        ReceivableRegister register = new ReceivableRegister();
        String manualKey = UUID.randomUUID().toString().replace("-", "");
        register.setInternalCode("RR-M-" + manualKey);
        register.setBusinessKey(ImportFileHasher.sha256(("manual|" + manualKey)
                .getBytes(StandardCharsets.UTF_8)));
        register.setStatus("DRAFT");
        register.setSourceVersion(1);
        // 面积/保证金原来在这里被写死成 0,编辑器填了也白填。现在由 copyEditable 落值,
        // 这里只兜底那两个编辑器不暴露的字段
        register.setSharedArea(BigDecimal.ZERO);
        register.setContractRentTotal(BigDecimal.ZERO);
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
        validateBinding(request, register.getTenantId());
        copyEditable(request, register);
        if (registerMapper.updateById(register) != 1) {
            throw new BizException("应收登记表已被修改，请刷新后重试");
        }
        return Result.ok();
    }

    /**
     * 确认一条手工新增的应收登记(草稿/待核对 → 已确认)。
     *
     * 手工新增写死 status=DRAFT,而「生成账单」只认 CONFIRMED/ACTIVE,原先又只有
     * 导入批次有确认通道 —— 于是手工新增出来的行永远是草稿、永远推不出账单,
     * 用户看到的就是「新增了个寂寞」。这里补上单行确认。
     *
     * 幂等:已确认/已生效重复调用直接返回。
     */
    @Operation(summary = "确认应收登记(草稿→已确认,确认后才能生成账单)")
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('finance:receivable:confirm')")
    @OperationLog(module = "应收明细", action = "确认登记")
    public Result<Void> confirmRegister(@PathVariable Long id) {
        ReceivableRegister register = registerMapper.selectById(id);
        if (register == null) {
            throw new BizException("应收登记表不存在");
        }
        if (List.of("CONFIRMED", "ACTIVE").contains(register.getStatus())) {
            return Result.ok();
        }
        if (!List.of("DRAFT", "PENDING_REVIEW").contains(register.getStatus())) {
            throw new BizException("只有草稿/待核对状态的登记可以确认");
        }
        // 出账按合同期限推账期,缺了起止就是一条确认了也出不了账的数据 —— 这关必须拦
        if (register.getContractStartDate() == null || register.getContractEndDate() == null) {
            throw new BizException("请先补齐合同起止日期再确认(生成账单要按合同期限推账期)");
        }
        if (register.getContractStartDate().isAfter(register.getContractEndDate())) {
            throw new BizException("合同起始日期不能晚于结束日期");
        }
        if (zero(register.getMonthlyRent()).add(zero(register.getMonthlyProperty())).signum() <= 0) {
            throw new BizException("月租金与月物业费不能同时为 0,请先补齐金额再确认");
        }
        // 出账认的是 tenantRefId / spaceId,不是登记表里的租户名文本。这里不拦的话,
        // 用户确认完去点「生成账单」才撞上"尚未完整绑定",要回头重填一遍
        if (register.getTenantRefId() == null
                || (register.getSpaceId() == null && register.getRoomId() == null)) {
            throw new BizException("请先在编辑里绑定「租户档案」与「空间」再确认(生成账单认的是绑定,不是文本名)");
        }
        int updated = registerMapper.update(new ReceivableRegister(),
                new LambdaUpdateWrapper<ReceivableRegister>()
                        .eq(ReceivableRegister::getId, id)
                        .in(ReceivableRegister::getStatus, "DRAFT", "PENDING_REVIEW")
                        .set(ReceivableRegister::getStatus, "CONFIRMED"));
        if (updated != 1) {
            throw new BizException("应收登记表已被修改，请刷新后重试");
        }
        return Result.ok();
    }

    /** 滞纳金起算日设置请求:日期为空 = 恢复默认口径(应收日与建单日取晚者) */
    public record LateFeeStartRequest(LocalDate lateFeeStartDate) {}

    @Operation(summary = "设置滞纳金起算日(该日之前不计滞纳金,逾期状态照标;传空恢复默认口径)")
    @PutMapping("/{id}/late-fee-start")
    @PreAuthorize("hasAuthority('finance:receivable:edit')")
    @OperationLog(module = "应收明细", action = "设置滞纳金起算日")
    public Result<Void> updateLateFeeStart(@PathVariable Long id,
                                           @RequestBody LateFeeStartRequest request) {
        if (registerMapper.selectById(id) == null) {
            throw new BizException("应收登记表不存在");
        }
        // 收缴政策字段,不是合同真相,已确认/已生效的登记也允许调整(普通编辑仍锁死)。
        // 置空要真写 NULL,走 wrapper.set;空实体一并传入让审计字段自动填充不掉队
        int updated = registerMapper.update(new ReceivableRegister(),
                new LambdaUpdateWrapper<ReceivableRegister>()
                        .eq(ReceivableRegister::getId, id)
                        .set(ReceivableRegister::getLateFeeStartDate, request.lateFeeStartDate()));
        if (updated != 1) {
            throw new BizException("应收登记表已被修改，请刷新后重试");
        }
        // 设置即生效:立刻按新起算日全量重算逾期滞纳金(幂等),不等每日自愈任务
        lateFeeService.recalc();
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

    @GetMapping("/import/{batchId}/provision/preview")
    @PreAuthorize("hasAuthority('finance:receivable:import')")
    @OperationLog(module = "应收明细", action = "补建主数据预览", saveParams = false)
    public Result<ReceivableProvisionPreview> provisionPreview(@PathVariable Long batchId) {
        return Result.ok(provisionService.preview(batchId));
    }

    @PostMapping("/import/{batchId}/provision")
    @PreAuthorize("hasAuthority('finance:receivable:import')")
    @OperationLog(module = "应收明细", action = "补建主数据")
    public Result<Void> provision(@PathVariable Long batchId,
                                  @RequestBody ReceivableProvisionRequest body) {
        provisionService.provision(batchId, body);
        return Result.ok();
    }

    @PostMapping("/import/{batchId}/confirm")
    @PreAuthorize("hasAuthority('finance:receivable:confirm')")
    @OperationLog(module = "应收明细", action = "确认导入")
    public Result<Map<String, Object>> confirm(@PathVariable long batchId) {
        int rows = importService.confirm(batchId, username());
        // 确认(独立事务)已提交,随即对该批登记自动生成账单:登记表是账单/收银台/逾期
        // 的唯一源头,下游自动派生,不再依赖有人挨条点"生成账单"。逐条独立事务、
        // billingKey 幂等,单条失败只计入 failed,不影响确认结果,每日自愈任务兜底重试
        List<Long> registerIds = registerMapper.selectList(new LambdaQueryWrapper<ReceivableRegister>()
                        .eq(ReceivableRegister::getSourceBatchId, batchId)
                        .in(ReceivableRegister::getStatus, "CONFIRMED", "ACTIVE"))
                .stream().map(ReceivableRegister::getId).toList();
        ReceivableAutoBillService.AutoBillSummary bills = autoBillService.generateFor(registerIds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("bills", bills);
        return Result.ok(out);
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
        if (zero(request.chargeArea()).signum() < 0 || zero(request.actualArea()).signum() < 0) {
            throw new BizException("面积不能为负数");
        }
        if (zero(request.rentDeposit()).signum() < 0 || zero(request.propertyDeposit()).signum() < 0) {
            throw new BizException("保证金不能为负数");
        }
        if (request.contractStartDate() != null && request.contractEndDate() != null
                && request.contractStartDate().isAfter(request.contractEndDate())) {
            throw new BizException("合同起始日期不能晚于结束日期");
        }
    }

    /**
     * 手工登记的租户/空间绑定校验。
     *
     * <p>登记表里的租户名、楼层名都只是文本,出账靠的是 tenantRefId 与 spaceId/roomId。
     * 手工新增原先根本不暴露这几个字段,于是新增出来的行确认了也生成不了账单
     * (「租户、空间或合同期限尚未完整绑定」)。这里与导入绑定共用同一套校验,
     * 不让手工建单绕开规则。</p>
     *
     * <p>草稿允许先不绑(边填边存),但只要动了其中任何一个,就要求绑成一套完整的。</p>
     */
    private void validateBinding(ReceivableUpsertRequest request, Long systemTenantId) {
        if (request.tenantRefId() == null && request.spaceId() == null
                && request.roomId() == null && request.contractId() == null) {
            return;
        }
        bindingValidator.validate(
                systemTenantId == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : systemTenantId,
                new ReceivableBindRequest(null, request.tenantRefId(), request.spaceId(),
                        request.roomId(), request.contractId()));
    }

    private static void copyEditable(ReceivableUpsertRequest request, ReceivableRegister target) {
        target.setSeqNo(request.seqNo());
        target.setAgreementNoRaw(request.agreementNoRaw());
        target.setTenantNameRaw(request.tenantNameRaw().trim());
        target.setSpaceNameRaw(request.spaceNameRaw().trim());
        target.setChargeArea(zero(request.chargeArea()));
        target.setActualArea(zero(request.actualArea()));
        target.setContractStartDate(request.contractStartDate());
        target.setContractEndDate(request.contractEndDate());
        target.setRentRateRaw(request.rentRateRaw());
        target.setPropertyRateRaw(request.propertyRateRaw());
        target.setFreePeriodRaw(request.freePeriodRaw());
        target.setFreeTermRaw(request.freeTermRaw());
        target.setDiscountRaw(request.discountRaw());
        target.setMonthlyRent(zero(request.monthlyRent()));
        target.setMonthlyProperty(zero(request.monthlyProperty()));
        target.setMonthlyTotal(zero(request.monthlyTotal()));
        target.setRentDeposit(zero(request.rentDeposit()));
        target.setPropertyDeposit(zero(request.propertyDeposit()));
        target.setCollectionTimingRaw(request.collectionTimingRaw());
        target.setFirstCollectionRaw(request.firstCollectionRaw());
        target.setNotesRaw(request.notesRaw());
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
    public record MonthlyLineItem(Long id, String tenantName, String spaceName,
                                  String agreementNo, String status,
                                  BigDecimal monthlyRent, BigDecimal monthlyProperty, BigDecimal monthlyTotal,
                                  java.time.LocalDate contractStart, java.time.LocalDate contractEnd,
                                  String freePeriod, String discount, String collectionTiming,
                                  java.time.LocalDate dueDate) {}
    public record MonthlySummaryResponse(String month, int count,
                                         BigDecimal totalRent, BigDecimal totalProperty, BigDecimal grandTotal,
                                         List<MonthlyLineItem> items) {}
}
