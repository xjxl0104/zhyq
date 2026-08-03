package com.zhyq.park.vending.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
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
import com.zhyq.park.vending.model.VendingImportType;
import com.zhyq.park.vending.service.VendingImportService;
import com.zhyq.park.vending.service.VendingTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "园区运营-自动售货机")
@RestController
@RequestMapping("/vending")
public class VendingController {
    private final VendingMachineMapper machineMapper;
    private final VendingSaleMapper saleMapper;
    private final VendingRestockMapper restockMapper;
    private final VendingFaultMapper faultMapper;
    private final VendingReconciliationMapper reconciliationMapper;
    private final VendingImportService importService;
    private final VendingTemplateService templateService;
    private final String externalUrl;

    public VendingController(
            VendingMachineMapper machineMapper, VendingSaleMapper saleMapper,
            VendingRestockMapper restockMapper, VendingFaultMapper faultMapper,
            VendingReconciliationMapper reconciliationMapper, VendingImportService importService,
            VendingTemplateService templateService,
            @Value("${zhyq.vending.external-url:https://fanmaiji.top/index?isFrom=login}") String externalUrl) {
        this.machineMapper = machineMapper;
        this.saleMapper = saleMapper;
        this.restockMapper = restockMapper;
        this.faultMapper = faultMapper;
        this.reconciliationMapper = reconciliationMapper;
        this.importService = importService;
        this.templateService = templateService;
        this.externalUrl = externalUrl;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('vending:open')")
    @OperationLog(module = "自动售货机", action = "读取厂商入口", saveParams = false)
    public Result<ExternalConfig> config() {
        return Result.ok(new ExternalConfig(externalUrl, false, false));
    }

    @PostMapping("/open-audit")
    @PreAuthorize("hasAuthority('vending:open')")
    @OperationLog(module = "自动售货机", action = "打开厂商系统", saveParams = false)
    public Result<Void> openAudit() {
        return Result.ok();
    }

    @GetMapping("/config/status")
    @PreAuthorize("hasAuthority('vending:config')")
    public Result<ConfigurationStatus> configurationStatus() {
        return Result.ok(new ConfigurationStatus(
                externalUrl != null && externalUrl.startsWith("https://"), false, false));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<VendingStats> stats() {
        long machines = count(machineMapper.selectCount(null));
        long online = count(machineMapper.selectCount(new LambdaQueryWrapper<VendingMachine>()
                .eq(VendingMachine::getRunningStatus, "在线")));
        long openFaults = count(faultMapper.selectCount(new LambdaQueryWrapper<VendingFault>()
                .in(VendingFault::getFaultStatus, "待处理", "处理中")));
        BigDecimal todaySales = saleMapper.selectList(new LambdaQueryWrapper<VendingSale>()
                        .ge(VendingSale::getPaymentTime, LocalDate.now().atStartOfDay())
                        .lt(VendingSale::getPaymentTime, LocalDate.now().plusDays(1).atStartOfDay())
                        .notIn(VendingSale::getOrderStatus, "已退款", "已取消"))
                .stream().map(VendingSale::getPaidAmount).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Result.ok(new VendingStats(machines, online, openFaults, todaySales));
    }

    @GetMapping("/machines")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<PageResult<VendingMachine>> machines(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = machineMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<VendingMachine>().orderByDesc(VendingMachine::getId));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<PageResult<VendingSale>> sales(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = saleMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<VendingSale>().orderByDesc(VendingSale::getPaymentTime));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/restocks")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<PageResult<VendingRestock>> restocks(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = restockMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<VendingRestock>().orderByDesc(VendingRestock::getRestockTime));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/faults")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<PageResult<VendingFault>> faults(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = faultMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<VendingFault>().orderByDesc(VendingFault::getOccurredTime));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/reconciliations")
    @PreAuthorize("hasAuthority('vending:query')")
    public Result<PageResult<VendingReconciliation>> reconciliations(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = reconciliationMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<VendingReconciliation>().orderByDesc(VendingReconciliation::getPeriodEnd));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('vending:import')")
    public ResponseEntity<byte[]> template(@RequestParam String type) {
        VendingImportType importType = VendingImportType.fromName(type);
        byte[] bytes = templateService.template(importType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("自动售货机-" + importType.sheetName() + "模板.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('vending:import')")
    @OperationLog(module = "自动售货机", action = "导入预览", saveParams = false)
    public Result<VendingImportPreview> preview(
            @RequestParam String type, @RequestPart("file") MultipartFile file) {
        return Result.ok(importService.preview(VendingImportType.fromName(type), file));
    }

    @PutMapping("/import/{batchId}/exclude")
    @PreAuthorize("hasAuthority('vending:import')")
    @OperationLog(module = "自动售货机", action = "排除错误行")
    public Result<VendingImportPreview> exclude(
            @PathVariable Long batchId, @RequestBody VendingExcludeRowsRequest request) {
        return Result.ok(importService.excludeRows(batchId, request));
    }

    @PostMapping("/import/{batchId}/confirm")
    @PreAuthorize("hasAuthority('vending:import')")
    @OperationLog(module = "自动售货机", action = "确认导入")
    public Result<Integer> confirm(@PathVariable Long batchId) {
        return Result.ok(importService.confirm(batchId, username()));
    }

    @PostMapping("/import/{batchId}/rollback")
    @PreAuthorize("hasAuthority('vending:import')")
    @OperationLog(module = "自动售货机", action = "撤销导入")
    public Result<Void> rollback(@PathVariable Long batchId) {
        importService.rollback(batchId, username());
        return Result.ok();
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }

    private static String username() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    public record ExternalConfig(String externalUrl, boolean apiAvailable, boolean nativeFormatSupported) {}
    public record ConfigurationStatus(boolean secureExternalUrl, boolean apiAvailable, boolean nativeFormatSupported) {}
    public record VendingStats(long machineCount, long onlineCount, long openFaultCount, BigDecimal todaySales) {}
}
