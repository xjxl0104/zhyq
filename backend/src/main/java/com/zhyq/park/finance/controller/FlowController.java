package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Flow;
import com.zhyq.park.finance.mapper.FlowMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "财务-收支流水")
@RestController
@RequestMapping("/finance/flow")
@RequiredArgsConstructor
public class FlowController {

    private final FlowMapper flowMapper;
    private final FinanceViewEnricher viewEnricher;

    /**
     * 分页查询流水。
     *
     * <p>原先整页只有「方向」一个筛选条件,点「重置」除了清掉那个下拉之外看不出任何变化,
     * 用户的反馈就是「点重置没反应」。这里把日常对账真正会用到的条件补齐:
     * 流水号、租客、匹配状态、流水时间范围 —— 有东西可清,重置才有意义。</p>
     *
     * <p>租客不是流水自己的字段(流水只存 billId),按租客筛要经账单绕一层,
     * 故用子查询而不是给 fin_flow 冗余一列 —— 冗余列会和账单改租客后不同步。</p>
     */
    @Operation(summary = "分页查询流水(支持流水号/租客/匹配状态/时间范围筛选)")
    @PreAuthorize("hasAuthority('finance:flow:query')")
    @GetMapping("/page")
    public Result<PageResult<Flow>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Integer direction,
                                         @RequestParam(required = false) Long billId,
                                         @RequestParam(required = false) String flowNo,
                                         @RequestParam(required = false) Long tenantRefId,
                                         @RequestParam(required = false) Integer matchStatus,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LambdaQueryWrapper<Flow> qw = new LambdaQueryWrapper<>();
        qw.eq(direction != null, Flow::getDirection, direction)
          .eq(billId != null, Flow::getBillId, billId)
          .eq(matchStatus != null, Flow::getMatchStatus, matchStatus)
          .like(StringUtils.hasText(flowNo), Flow::getFlowNo, flowNo)
          // 结束日当天的流水也要算进来:flow_time 是 datetime,按 < 次日零点收口
          .ge(startDate != null, Flow::getFlowTime, startDate == null ? null : startDate.atStartOfDay())
          .lt(endDate != null, Flow::getFlowTime, endDate == null ? null : endDate.plusDays(1).atStartOfDay())
          .inSql(tenantRefId != null, Flow::getBillId,
                  "SELECT id FROM fin_bill WHERE deleted = 0 AND tenant_ref_id = " + tenantRefId)
          .orderByDesc(Flow::getId);
        IPage<Flow> p = flowMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        enrichFlow(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 填上关联账单号、租客名与费用类型。
     *
     * <p>本页此前只显示 {@code billId} —— 一个裸数字,既看不出是哪个租客的钱,
     * 也没法和账单页对上账。口径与所有账单页共用 {@link FinanceViewEnricher}。</p>
     */
    private void enrichFlow(java.util.List<Flow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.Map<Long, FinanceViewEnricher.BillView> views = viewEnricher.resolveBillViews(
                rows.stream().map(Flow::getBillId).toList());
        for (Flow row : rows) {
            FinanceViewEnricher.BillView v = views.get(row.getBillId());
            if (v == null) {
                continue;
            }
            row.setBillCode(v.billCode());
            row.setTenantName(v.tenantName());
            row.setFeeType(v.feeType());
        }
    }
}
