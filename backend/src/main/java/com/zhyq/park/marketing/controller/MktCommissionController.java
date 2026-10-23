package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktSettleBatch;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktSettleBatchMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "全民营销-佣金结算")
@RestController
@RequestMapping("/crm/marketing/commission")
@RequiredArgsConstructor
public class MktCommissionController {

    private final MktPromoterCommissionMapper commissionMapper;
    private final MktSettleBatchMapper batchMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktCommissionService commissionService;
    /** 非构造注入：列表里带出订单的发货日(event_time)和订单号。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.zhyq.park.marketing.mapper.MktReferralOrderMapper orderMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.zhyq.park.marketing.service.MktMonthlySettlementService monthlyService;

    @Operation(summary = "按月汇总：一个月×一个合同一行") @PreAuthorize("hasAuthority('crm:marketing:commission:query')") @GetMapping("/monthly")
    public Result<List<Map<String, Object>>> monthly(@RequestParam(required = false) Long promoterId,
                                                      @RequestParam(required = false) String promoterName) {
        List<Long> ids = null;
        if (promoterId != null) ids = List.of(promoterId);
        if (promoterName != null && !promoterName.isBlank()) {
            String kw = promoterName.trim();
            List<Long> byName = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                    .and(q -> q.like(MktPromoter::getName, kw).or().like(MktPromoter::getPhone, kw))
                    .select(MktPromoter::getId)).stream().map(MktPromoter::getId).toList();
            ids = ids == null ? byName : ids.stream().filter(byName::contains).toList();
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Map<String, Object> r : monthlyService.monthlySummary(ids)) {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r.get("ym")); m.put("contractId", r.get("contract_id")); m.put("contractNo", r.get("contract_no"));
            m.put("startDate", r.get("start_date") == null ? null : String.valueOf(r.get("start_date")));
            m.put("customerName", r.get("customer_name")); m.put("promoters", r.get("promoters"));
            m.put("tickets", r.get("tickets")); m.put("total", r.get("total")); m.put("frozen", r.get("frozen"));
            m.put("settleable", r.get("settleable")); m.put("settled", r.get("settled"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @Operation(summary = "分页") @PreAuthorize("hasAuthority('crm:marketing:commission:query')") @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) Long promoterId,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Long referralOrderId,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String promoterName,
                                                        @RequestParam(required = false) Long contractId,
                                                        @RequestParam(required = false) String month) {
        if (month != null && !month.matches("^\\d{4}-(0[1-9]|1[0-2])$")) throw new com.zhyq.park.common.exception.BizException("月份格式应为 yyyy-MM");
        // 伙伴姓名/手机号模糊搜索:先找出匹配的伙伴 id,没有匹配就直接查不到
        List<Long> nameIds = null;
        if (promoterName != null && !promoterName.isBlank()) {
            String kw = promoterName.trim();
            nameIds = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                    .and(q -> q.like(MktPromoter::getName, kw).or().like(MktPromoter::getPhone, kw))
                    .select(MktPromoter::getId)).stream().map(MktPromoter::getId).toList();
        }
        LambdaQueryWrapper<MktPromoterCommission> qw = new LambdaQueryWrapper<MktPromoterCommission>()
                .eq(promoterId != null, MktPromoterCommission::getPromoterId, promoterId)
                .in(nameIds != null && !nameIds.isEmpty(), MktPromoterCommission::getPromoterId, nameIds)
                .apply(nameIds != null && nameIds.isEmpty(), "1 = 0")
                .inSql(contractId != null && month != null, MktPromoterCommission::getReferralOrderId,
                        "SELECT id FROM crm_referral_order WHERE deleted = 0 AND source_type = " + MktCommissionService.SOURCE_OUTBOUND
                                + " AND source_id = " + contractId + " AND DATE_FORMAT(event_time, '%Y-%m') = '" + month + "'")
                .eq(status != null, MktPromoterCommission::getStatus, status)
                .eq(referralOrderId != null, MktPromoterCommission::getReferralOrderId, referralOrderId)
                .and(projectId != null, q -> q.eq(MktPromoterCommission::getProjectId, projectId).or().isNull(MktPromoterCommission::getProjectId))
                .orderByDesc(MktPromoterCommission::getId);
        IPage<MktPromoterCommission> p = commissionMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        Map<Long, String> names = new HashMap<>();
        // 发货日 = 订单的 event_time；“解冻时间”是发货日加冻结期，两者不是一回事
        Map<Long, com.zhyq.park.marketing.entity.MktReferralOrder> orders = new HashMap<>();
        List<Long> orderIds = p.getRecords().stream().map(MktPromoterCommission::getReferralOrderId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (orderMapper != null && !orderIds.isEmpty()) {
            orderMapper.selectBatchIds(orderIds).forEach(o -> orders.put(o.getId(), o));
        }
        List<Map<String, Object>> rows = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId()); m.put("referralOrderId", c.getReferralOrderId()); m.put("promoterId", c.getPromoterId());
            m.put("withdrawnAmount", c.getWithdrawnAmount()); m.put("withdrawalAmount", c.getWithdrawalAmount());
            m.put("pricingId", c.getPricingId()); m.put("amountPerOrder", c.getAmountPerOrder()); m.put("orderCount", c.getOrderCount());
            m.put("positionCode", c.getPositionCode()); m.put("sharePct", c.getSharePct()); m.put("diffPct", c.getDiffPct());
            m.put("baseAmount", c.getBaseAmount()); m.put("rate", c.getRate()); m.put("amount", c.getAmount()); m.put("sign", c.getSign());
            m.put("status", c.getStatus()); m.put("unfreezeAt", c.getUnfreezeAt()); m.put("settleBatchId", c.getSettleBatchId());
            m.put("withdrawalId", c.getWithdrawalId()); m.put("voidReason", c.getVoidReason()); m.put("createTime", c.getCreateTime());
            var order = orders.get(c.getReferralOrderId());
            m.put("orderEventTime", order == null ? null : order.getEventTime());
            m.put("orderSourceNo", order == null ? null : order.getSourceNo());
            m.put("promoterName", names.computeIfAbsent(c.getPromoterId(), id -> {
                MktPromoter pr = promoterMapper.selectById(id); return pr == null ? null : pr.getName();
            }));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @Operation(summary = "批量结算(可结算 → 已结算),返回批次号") @PreAuthorize("hasAuthority('crm:marketing:commission:pay')") @PostMapping("/settle")
    public Result<String> settle(@RequestBody Map<String, List<Long>> body) {
        return Result.ok(commissionService.settle(body.get("ids"), MktAuditService.currentOperator()));
    }

    @Operation(summary = "按合同月结:结算该合同指定月份(默认上个月)的出库单佣金;上个月仅限每月3-10号,更早月份可随时补结,不能早于合同开始月")
    @PreAuthorize("hasAuthority('crm:marketing:commission:pay')") @PostMapping("/settle-contract")
    public Result<Map<String, Object>> settleContract(@RequestBody Map<String, Object> body) {
        Long contractId = longOf(body == null ? null : body.get("contractId"));
        if (contractId == null) throw new BizException("请选择服务合同");
        MktServiceContract contract = contractMapper.selectById(contractId);
        if (contract == null) throw new BizException("服务合同不存在");
        LocalDate today = LocalDate.now();
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        YearMonth target = monthOf(body == null ? null : body.get("month"), lastMonth);
        if (target.isAfter(lastMonth)) throw new BizException("只能结算当月之前已结束的月份");
        if (contract.getStartDate() != null && target.isBefore(YearMonth.from(contract.getStartDate())))
            throw new BizException("结算月份不能早于合同开始月份 " + YearMonth.from(contract.getStartDate()));
        if (target.equals(lastMonth) && (today.getDayOfMonth() < 3 || today.getDayOfMonth() > 10))
            throw new BizException("结算上个月佣金仅限每月 3-10 号操作；更早月份可随时补结");
        LocalDate periodStart = target.atDay(1);
        LocalDate nextStart = target.plusMonths(1).atDay(1);
        // 该合同上月出库单(含拆单后的全部佣金行),冻结期未到/已结算的行分开统计
        String inSql = "SELECT id FROM crm_referral_order WHERE deleted = 0 AND source_type = "
                + MktCommissionService.SOURCE_OUTBOUND + " AND source_id = " + contractId
                + " AND event_time >= '" + periodStart + " 00:00:00' AND event_time < '" + nextStart + " 00:00:00'";
        List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                .inSql(MktPromoterCommission::getReferralOrderId, inSql)
                .eq(MktPromoterCommission::getSign, 1)
                .isNull(MktPromoterCommission::getWithdrawalId));
        List<Long> ids = new ArrayList<>();
        int frozen = 0, already = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (MktPromoterCommission c : rows) {
            if (Integer.valueOf(MktCommissionService.C_SETTLEABLE).equals(c.getStatus())) {
                ids.add(c.getId());
                amount = amount.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            } else if (Integer.valueOf(MktCommissionService.C_FROZEN).equals(c.getStatus())) {
                frozen++;
            } else if (Integer.valueOf(MktCommissionService.C_SETTLED).equals(c.getStatus())
                    || Integer.valueOf(MktCommissionService.C_WITHDRAWN).equals(c.getStatus())) {
                already++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("contractNo", contract.getContractNo());
        result.put("periodStart", periodStart.toString());
        result.put("periodEnd", nextStart.minusDays(1).toString());
        result.put("month", target.toString());
        result.put("settleCount", ids.size());
        result.put("settleAmount", amount);
        result.put("frozenCount", frozen);
        result.put("alreadySettledCount", already);
        result.put("batchNo", ids.isEmpty() ? null
                : commissionService.settle(ids, MktAuditService.currentOperator()));
        return Result.ok(result);
    }

    private static Long longOf(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(value.toString().trim()); }
        catch (NumberFormatException e) { throw new BizException("服务合同编号不正确"); }
    }

    /** month 形如 2026-06；缺省为 fallback(上个月)。 */
    private static YearMonth monthOf(Object value, YearMonth fallback) {
        if (value == null || value.toString().isBlank()) return fallback;
        try { return YearMonth.parse(value.toString().trim()); }
        catch (java.time.format.DateTimeParseException e) { throw new BizException("结算月份格式应为 yyyy-MM"); }
    }

    @Operation(summary = "作废单条") @PreAuthorize("hasAuthority('crm:marketing:commission:edit')") @PostMapping("/{id}/void")
    public Result<Void> voidOne(@PathVariable Long id, @RequestBody Map<String, String> body) {
        commissionService.voidCommission(id, body.get("reason")); return Result.ok();
    }

    @Operation(summary = "结算批次") @PreAuthorize("hasAuthority('crm:marketing:commission:query')") @GetMapping("/batches")
    public Result<PageResult<MktSettleBatch>> batches(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        IPage<MktSettleBatch> p = batchMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktSettleBatch>().orderByDesc(MktSettleBatch::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
