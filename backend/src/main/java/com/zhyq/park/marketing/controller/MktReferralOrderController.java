package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "全民营销-计佣订单")
@RestController
@RequestMapping("/crm/marketing/order")
@RequiredArgsConstructor
public class MktReferralOrderController {

    private final MktReferralOrderMapper orderMapper;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktPromoterMapper promoterMapper;
    private final CustomerMapper customerMapper;
    private final MktReferralOrderImportService importService;
    private final MktCommissionService commissionService;
    private final MktAuditService auditService;

    @Operation(summary = "分页(附客户/伙伴名)")
    @PreAuthorize("hasAuthority('crm:marketing:order:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String sourceNo,
                                                        @RequestParam(required = false) Integer sourceType,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktReferralOrder> qw = new LambdaQueryWrapper<MktReferralOrder>()
                .like(StringUtils.hasText(sourceNo), MktReferralOrder::getSourceNo, sourceNo)
                .eq(sourceType != null, MktReferralOrder::getSourceType, sourceType)
                .eq(status != null, MktReferralOrder::getStatus, status)
                .eq(promoterId != null, MktReferralOrder::getPromoterId, promoterId)
                .eq(projectId != null, MktReferralOrder::getProjectId, projectId)
                .orderByDesc(MktReferralOrder::getId);
        IPage<MktReferralOrder> p = orderMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(this::enrich).collect(Collectors.toList())));
    }

    @Operation(summary = "详情") @PreAuthorize("hasAuthority('crm:marketing:order:query')") @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MktReferralOrder o = orderMapper.selectById(id);
        if (o == null) throw new BizException("订单不存在");
        return Result.ok(enrich(o));
    }

    @Operation(summary = "该订单拆出的佣金行") @PreAuthorize("hasAuthority('crm:marketing:order:query')") @GetMapping("/{id}/splits")
    public Result<List<MktPromoterCommission>> splits(@PathVariable Long id) {
        return Result.ok(commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(MktPromoterCommission::getReferralOrderId, id).orderByAsc(MktPromoterCommission::getId)));
    }

    @Operation(summary = "导入出库单 Excel(文件方式,阶段 A)") @PreAuthorize("hasAuthority('crm:marketing:order:edit')") @PostMapping("/import")
    public Result<MktReferralOrderImportService.ImportResult> importExcel(@RequestParam("file") MultipartFile file,
                                                                          @RequestParam(required = false) Long projectId) {
        return Result.ok(importService.importWorkbook(file, projectId));
    }

    @Operation(summary = "下载导入模板") @PreAuthorize("hasAuthority('crm:marketing:order:query')") @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("出库单");
            Row h = sheet.createRow(0);
            String[] heads = {"出库单号", "客户手机号", "件数", "包裹数", "发货时间", "物流单号", "云仓编码", "货值"};
            for (int i = 0; i < heads.length; i++) h.createCell(i).setCellValue(heads[i]);
            Row ex = sheet.createRow(1);
            Object[] sample = {"OUT20260918000123", "13800000001", 3, 1, "2026-09-18 10:20:00", "SF123456789", "WH-HZ-001", 1180.00};
            for (int i = 0; i < sample.length; i++) {
                if (sample[i] instanceof Number n) ex.createCell(i).setCellValue(n.doubleValue());
                else ex.createCell(i).setCellValue(String.valueOf(sample[i]));
            }
            wb.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=outbound-import-template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    @Operation(summary = "作废订单:未结算佣金作废,已结算生成扣回") @PreAuthorize("hasAuthority('crm:marketing:order:edit')") @PostMapping("/{id}/void")
    @Transactional
    public Result<Void> voidOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (!StringUtils.hasText(reason)) throw new BizException("请填写作废原因");
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getId, id).eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .set(MktReferralOrder::getStatus, MktCommissionService.ORDER_CANCELLED).set(MktReferralOrder::getRemark, reason));
        if (updated == 0) throw new BizException("订单状态已变化");
        commissionService.clawback(id, reason);
        auditService.log("order.void", "referral_order", id, reason);
        return Result.ok();
    }

    private Map<String, Object> enrich(MktReferralOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId()); m.put("sourceType", o.getSourceType()); m.put("sourceNo", o.getSourceNo()); m.put("sourceId", o.getSourceId());
        m.put("customerId", o.getCustomerId()); m.put("promoterId", o.getPromoterId()); m.put("customerGrade", o.getCustomerGrade());
        m.put("poolFactor", o.getPoolFactor()); m.put("baseMode", o.getBaseMode()); m.put("baseAmount", o.getBaseAmount());
        m.put("poolAmount", o.getPoolAmount()); m.put("qty", o.getQty()); m.put("packages", o.getPackages()); m.put("serviceFee", o.getServiceFee());
        m.put("status", o.getStatus()); m.put("eventTime", o.getEventTime()); m.put("warehouseId", o.getWarehouseId()); m.put("remark", o.getRemark());
        Customer c = o.getCustomerId() == null ? null : customerMapper.selectById(o.getCustomerId());
        m.put("customerName", c == null ? null : c.getName());
        MktPromoter p = promoterMapper.selectById(o.getPromoterId());
        m.put("promoterName", p == null ? null : p.getName());
        return m;
    }
}
