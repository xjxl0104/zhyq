package com.zhyq.park.energy.controller;

import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.LeaseContract;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.service.LeaseFeeExportService;
import com.zhyq.park.energy.service.LeaseFeePaymentService;
import com.zhyq.park.energy.service.LeaseFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 缴费管理 - 租金与物业管理费(按水电费年月表模式,租金与物业管理费收取单位独立)。
 */
@Tag(name = "缴费管理-租金与物业管理费")
@RestController
@RequestMapping("/energy/lease-fee")
@RequiredArgsConstructor
public class LeaseFeeController {

    private final LeaseFeeService service;
    private final LeaseFeePaymentService payments;
    private final LeaseFeeExportService exportService;

    @Data
    public static class OpenRequest {
        private String period;
        private Long projectId;
    }

    @Data
    public static class IdsRequest {
        private List<Long> ids;
    }

    public record PaymentRequest(Object paymentStatus) {}

    // ---------- 合同基础台账 ----------

    @Operation(summary = "查询租金/物业费计费合同台账")
    @GetMapping("/contracts")
    public Result<List<LeaseContract>> contracts(@RequestParam(defaultValue = "true") boolean includeDisabled) {
        return Result.ok(service.listContracts(includeDisabled));
    }

    @Operation(summary = "新增或修改计费合同")
    @OperationLog(module = "租金物业费合同", action = "保存")
    @PostMapping("/contracts")
    public Result<LeaseContract> saveContract(@RequestBody LeaseContract contract) {
        return Result.ok(service.saveContract(contract));
    }

    @Operation(summary = "删除计费合同")
    @OperationLog(module = "租金物业费合同", action = "删除")
    @DeleteMapping("/contracts/{id}")
    public Result<Void> deleteContract(@PathVariable Long id) {
        service.deleteContract(id);
        return Result.ok();
    }

    // ---------- 年月表与账期 ----------

    @Operation(summary = "年月表:某一年12个月的租金与物业管理费汇总")
    @GetMapping("/overview")
    public Result<List<LeaseFeeService.MonthOverview>> overview(@RequestParam int year) {
        return Result.ok(service.overview(year));
    }

    @Operation(summary = "打开某月账期(无则新建,抬头与两类收取单位沿用上月)")
    @PostMapping("/open")
    public Result<LeaseFeePeriod> open(@RequestBody OpenRequest req) {
        return Result.ok(service.open(req.getPeriod(), req.getProjectId()));
    }

    @Operation(summary = "账期详情")
    @GetMapping("/{id}")
    public Result<LeaseFeePeriod> get(@PathVariable Long id) {
        return Result.ok(service.require(id));
    }

    @Operation(summary = "更新账期抬头与租金/物业管理费收取单位")
    @OperationLog(module = "租金物业费账期", action = "修改抬头")
    @PutMapping
    public Result<LeaseFeePeriod> update(@RequestBody LeaseFeePeriod period) {
        return Result.ok(service.updatePeriod(period));
    }

    @Operation(summary = "月度租金/物业费工作表")
    @GetMapping("/{id}/sheet")
    public Result<LeaseFeeService.Sheet> sheet(@PathVariable Long id,
                                               @RequestParam(defaultValue = "RENT") String feeType) {
        return Result.ok(service.sheet(id, feeType));
    }

    @Operation(summary = "批量保存当月合同减免系数与备注调整")
    @OperationLog(module = "租金物业费账期", action = "保存月度调整")
    @PostMapping("/{id}/entries")
    public Result<Map<String, Object>> saveEntries(@PathVariable Long id,
                                                   @RequestBody List<LeaseFeeService.EntryInput> inputs) {
        return Result.ok(Map.of("saved", service.saveEntries(id, inputs)));
    }

    // ---------- 通知单与出账 ----------

    @Operation(summary = "生成/重新生成本期租金与物业管理费通知单(按收取单位分别出单)")
    @OperationLog(module = "租金物业费账期", action = "生成通知单")
    @PostMapping("/{id}/generate")
    public Result<LeaseFeeService.GenerateResult> generate(@PathVariable Long id) {
        return Result.ok(service.generate(id));
    }

    @Operation(summary = "查询本期全部租金与物业管理费通知单")
    @GetMapping("/{id}/notices")
    public Result<List<LeaseFeeNotice>> notices(@PathVariable Long id) {
        return Result.ok(service.notices(id));
    }

    @Operation(summary = "调整单张租金/物业管理费通知单")
    @OperationLog(module = "租金物业费账期", action = "调整通知单")
    @PostMapping("/notice/edit")
    public Result<LeaseFeeNotice> editNotice(@RequestBody LeaseFeeService.NoticeEdit edit) {
        return Result.ok(service.editNotice(edit));
    }

    @Operation(summary = "记录通知单缴费状态")
    @OperationLog(module = "租金物业费账期", action = "记录缴费状态")
    @PutMapping("/notice/{id}/payment")
    public Result<LeaseFeeNotice> payment(@PathVariable Long id, @RequestBody PaymentRequest req) {
        if (req == null || !(req.paymentStatus() instanceof Integer status)) {
            throw new BizException(400, "缴费状态只能选择未缴或已缴");
        }
        return Result.ok(payments.updateStatus(id, status));
    }

    @Operation(summary = "删除指定通知单")
    @OperationLog(module = "租金物业费账期", action = "删除通知单")
    @PostMapping("/{id}/notices/delete")
    public Result<Map<String, Object>> deleteNotices(@PathVariable Long id, @RequestBody IdsRequest req) {
        return Result.ok(service.deleteNotices(id, req.getIds(), false));
    }

    @Operation(summary = "删除本期全部通知单")
    @OperationLog(module = "租金物业费账期", action = "删除全部通知单")
    @PostMapping("/{id}/notices/delete-all")
    public Result<Map<String, Object>> deleteAllNotices(@PathVariable Long id) {
        return Result.ok(service.deleteNotices(id, null, true));
    }

    @Operation(summary = "确认出账(租金与物业费分别生成财务账单并冻结本月)")
    @OperationLog(module = "租金物业费账期", action = "确认出账")
    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    @Operation(summary = "撤销确认")
    @OperationLog(module = "租金物业费账期", action = "撤销确认")
    @PostMapping("/{id}/revoke")
    public Result<Map<String, Object>> revoke(@PathVariable Long id) {
        return Result.ok(service.revoke(id));
    }

    @Operation(summary = "导出当月租金与物业管理费 Excel")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        LeaseFeePeriod p = service.require(id);
        byte[] body = exportService.export(p, service.sheet(id, "ALL"), service.notices(id));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(p.getPeriod() + "租金与物业管理费明细表.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
