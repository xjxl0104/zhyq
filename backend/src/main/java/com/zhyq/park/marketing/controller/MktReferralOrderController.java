package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final MktServiceContractMapper serviceContractMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final MktReferralOrderImportService importService;
    private final MktCommissionService commissionService;
    private final MktAuditService auditService;
    private final com.zhyq.park.marketing.mapper.MktServiceFeeBillLineMapper billLines;

    /** sourceId 指向 crm_service_contract.id 的来源:出库单/平台费收款/签约奖;租赁签约的 sourceId 指向 biz_contract。 */
    private static final List<Integer> SERVICE_CONTRACT_SOURCES = List.of(MktCommissionService.SOURCE_OUTBOUND,
            MktCommissionService.SOURCE_PLATFORM_FEE, MktCommissionService.SOURCE_CONTRACT_BONUS);

    @Operation(summary = "分页(附客户/伙伴/合同/云仓信息)")
    @PreAuthorize("hasAuthority('crm:marketing:order:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String sourceNo,
                                                        @RequestParam(required = false) Integer sourceType,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Long customerId,
                                                        @RequestParam(required = false) Long warehouseId,
                                                        @RequestParam(required = false) String contractNo,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktReferralOrder> qw = new LambdaQueryWrapper<MktReferralOrder>()
                .like(StringUtils.hasText(sourceNo), MktReferralOrder::getSourceNo, sourceNo)
                .eq(sourceType != null, MktReferralOrder::getSourceType, sourceType)
                .eq(status != null, MktReferralOrder::getStatus, status)
                .eq(promoterId != null, MktReferralOrder::getPromoterId, promoterId)
                .eq(customerId != null, MktReferralOrder::getCustomerId, customerId)
                .eq(warehouseId != null, MktReferralOrder::getWarehouseId, warehouseId)
                .and(projectId != null, q -> q.eq(MktReferralOrder::getProjectId, projectId).or().isNull(MktReferralOrder::getProjectId))
                .orderByDesc(MktReferralOrder::getId);
        if (StringUtils.hasText(contractNo)) {
            // 合同号模糊 → 只匹配 sourceId 指向服务合同的来源;租赁签约不参与合同号匹配。
            List<Long> contractIds = serviceContractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                            .like(MktServiceContract::getContractNo, contractNo)
                            .select(MktServiceContract::getId))
                    .stream().map(MktServiceContract::getId).collect(Collectors.toList());
            if (contractIds.isEmpty()) return Result.ok(PageResult.of(0, Collections.emptyList()));
            qw.in(MktReferralOrder::getSourceId, contractIds).in(MktReferralOrder::getSourceType, SERVICE_CONTRACT_SOURCES);
        }
        IPage<MktReferralOrder> p = orderMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(this::enrich).collect(Collectors.toList())));
    }

    @Operation(summary = "按服务合同汇总出库计佣(分页)")
    @PreAuthorize("hasAuthority('crm:marketing:order:query')")
    @GetMapping("/contract-summary")
    public Result<PageResult<Map<String, Object>>> contractSummary(@RequestParam(defaultValue = "1") int pageNo,
                                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                                   @RequestParam(required = false) String contractNo,
                                                                   @RequestParam(required = false) Long promoterId,
                                                                   @RequestParam(required = false) Long customerId,
                                                                   @RequestParam(required = false) Long warehouseId,
                                                                   @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktServiceContract> cw = new LambdaQueryWrapper<MktServiceContract>()
                .like(StringUtils.hasText(contractNo), MktServiceContract::getContractNo, contractNo)
                // 合同上的成交伙伴快照
                .eq(promoterId != null, MktServiceContract::getPartnerId, promoterId)
                .eq(customerId != null, MktServiceContract::getCustomerId, customerId)
                .eq(warehouseId != null, MktServiceContract::getWarehouseId, warehouseId)
                .eq(projectId != null, MktServiceContract::getProjectId, projectId)
                .orderByDesc(MktServiceContract::getId);
        IPage<MktServiceContract> cp = serviceContractMapper.selectPage(new Page<>(pageNo, pageSize), cw);
        List<MktServiceContract> contracts = cp.getRecords();

        Map<Long, Map<String, Object>> agg = aggregateByContract(contracts);
        Map<Long, String> customerNames = new HashMap<>();
        Set<Long> customerIds = contracts.stream().map(MktServiceContract::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!customerIds.isEmpty()) {
            for (Customer c : customerMapper.selectBatchIds(customerIds)) customerNames.put(c.getId(), c.getName());
        }
        Map<Long, String> partnerNames = new HashMap<>();
        Set<Long> partnerIds = contracts.stream().map(MktServiceContract::getPartnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!partnerIds.isEmpty()) {
            for (MktPromoter p : promoterMapper.selectBatchIds(partnerIds)) partnerNames.put(p.getId(), p.getName());
        }
        Map<Long, String> warehouseNames = new HashMap<>();
        Set<Long> warehouseIds = contracts.stream().map(MktServiceContract::getWarehouseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!warehouseIds.isEmpty()) {
            for (MktWarehouse w : warehouseMapper.selectBatchIds(warehouseIds)) warehouseNames.put(w.getId(), w.getName());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MktServiceContract c : contracts) {
            Map<String, Object> m = new HashMap<>();
            m.put("contractId", c.getId());
            m.put("contractNo", c.getContractNo());
            m.put("signMode", c.getSignMode());
            m.put("serviceType", c.getServiceType());
            m.put("feeModel", c.getFeeModel());
            m.put("startDate", c.getStartDate());
            m.put("endDate", c.getEndDate());
            m.put("customerId", c.getCustomerId());
            m.put("customerName", customerNames.get(c.getCustomerId()));
            m.put("partnerId", c.getPartnerId());
            m.put("partnerName", partnerNames.get(c.getPartnerId()));
            m.put("warehouseId", c.getWarehouseId());
            m.put("warehouseName", warehouseNames.get(c.getWarehouseId()));
            Map<String, Object> a = agg.getOrDefault(c.getId(), Map.of());
            m.put("orderCount", a.getOrDefault("orderCount", 0));
            m.put("confirmedCount", a.getOrDefault("confirmedCount", 0));
            m.put("serviceFeeSum", a.getOrDefault("serviceFeeSum", BigDecimal.ZERO));
            m.put("poolSum", a.getOrDefault("poolSum", BigDecimal.ZERO));
            m.put("lastEventTime", a.get("lastEventTime"));
            rows.add(m);
        }
        return Result.ok(PageResult.of(cp.getTotal(), rows));
    }

    /**
     * 一批合同 → 出库单(source_type=2)聚合:总单量/已确认单量/服务费合计/佣金池合计/最近事件时间。
     * 金额只计已确认订单,与服务费账单、营销看板同口径;作废/退款的订单不能留在合计里。
     */
    private Map<Long, Map<String, Object>> aggregateByContract(List<MktServiceContract> contracts) {
        if (contracts.isEmpty()) return Map.of();
        List<Long> ids = contracts.stream().map(MktServiceContract::getId).collect(Collectors.toList());
        String confirmed = "status = " + MktCommissionService.ORDER_CONFIRMED;
        QueryWrapper<MktReferralOrder> gw = new QueryWrapper<MktReferralOrder>()
                .select("source_id",
                        "COUNT(*) AS orderCount",
                        "SUM(CASE WHEN " + confirmed + " THEN 1 ELSE 0 END) AS confirmedCount",
                        "IFNULL(SUM(CASE WHEN " + confirmed + " THEN service_fee ELSE 0 END), 0) AS serviceFeeSum",
                        "IFNULL(SUM(CASE WHEN " + confirmed + " THEN pool_amount ELSE 0 END), 0) AS poolSum",
                        "MAX(event_time) AS lastEventTime")
                .eq("source_type", MktCommissionService.SOURCE_OUTBOUND)
                .in("source_id", ids)
                .groupBy("source_id");
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(gw)) {
            Object sid = row.get("source_id");
            if (sid == null) continue;
            Map<String, Object> clean = new HashMap<>(row);
            clean.remove("source_id");
            result.put(Long.valueOf(sid.toString()), clean);
        }
        return result;
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
        // The order update holds its row lock. Billing takes the same lock before
        // inserting a snapshot, so cancellation cannot race a new bill into existence.
        if (billLines.selectCount(new LambdaQueryWrapper<com.zhyq.park.marketing.entity.MktServiceFeeBillLine>()
                .eq(com.zhyq.park.marketing.entity.MktServiceFeeBillLine::getReferralOrderId, id)) > 0)
            throw new BizException("该订单已进入服务费账单，请先联系财务更正账单，不能直接作废");
        commissionService.clawback(id, reason);
        auditService.log("order.void", "referral_order", id, reason);
        return Result.ok();
    }

    private Map<String, Object> enrich(MktReferralOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId()); m.put("sourceType", o.getSourceType()); m.put("sourceNo", o.getSourceNo()); m.put("sourceId", o.getSourceId());
        m.put("customerId", o.getCustomerId()); m.put("promoterId", o.getPromoterId()); m.put("customerGrade", o.getCustomerGrade());
        m.put("customerCode", o.getCustomerCode());
        m.put("pricingId", o.getPricingId()); m.put("commissionUnitPrice", o.getCommissionUnitPrice());
        m.put("commissionOrderCount", o.getCommissionOrderCount()); m.put("companyPerOrder", o.getCompanyPerOrder());
        m.put("poolFactor", o.getPoolFactor()); m.put("baseMode", o.getBaseMode()); m.put("baseAmount", o.getBaseAmount());
        m.put("poolAmount", o.getPoolAmount()); m.put("qty", o.getQty()); m.put("packages", o.getPackages()); m.put("serviceFee", o.getServiceFee());
        m.put("status", o.getStatus()); m.put("eventTime", o.getEventTime()); m.put("warehouseId", o.getWarehouseId()); m.put("remark", o.getRemark());
        Customer c = o.getCustomerId() == null ? null : customerMapper.selectById(o.getCustomerId());
        m.put("customerName", c == null ? null : c.getName());
        MktPromoter p = o.getPromoterId() == null ? null : promoterMapper.selectById(o.getPromoterId());
        m.put("promoterName", p == null ? null : p.getName());
        if (o.getWarehouseId() != null) {
            MktWarehouse w = warehouseMapper.selectById(o.getWarehouseId());
            if (w != null) { m.put("warehouseName", w.getName()); m.put("warehouseCode", w.getCode()); }
        }
        // 出库单/平台费/签约奖的 sourceId = crm_service_contract.id → 带出合同号与签约方式(与合同号筛选同口径)
        if (o.getSourceId() != null && SERVICE_CONTRACT_SOURCES.contains(o.getSourceType())) {
            MktServiceContract sc = serviceContractMapper.selectById(o.getSourceId());
            if (sc != null) {
                m.put("contractId", sc.getId());
                m.put("contractNo", sc.getContractNo());
                m.put("signMode", sc.getSignMode());
            }
        }
        return m;
    }
}
