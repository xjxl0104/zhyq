package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.audit.OperationLog;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.EnergyAllocation;
import com.zhyq.park.energy.entity.UtilityBill;
import com.zhyq.park.energy.mapper.EnergyAllocationMapper;
import com.zhyq.park.energy.mapper.UtilityBillMapper;
import com.zhyq.park.energy.service.AllocationService;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度公用事业账单与公摊出账。
 *
 * <p>流程:录发票(总用量/不含税总额/税率) → 测算(按《附件二》四个公式) →
 * 确认出账(每个租户一张能源费账单)。测算随便重跑,确认后锁定。</p>
 */
@Tag(name = "能耗管理-公用事业账单与分摊")
@RestController
@RequestMapping("/energy/utility-bill")
@RequiredArgsConstructor
public class UtilityBillController {

    private static final String FEE_TYPE_ENERGY = "能源费";

    private final UtilityBillMapper utilityBillMapper;
    private final EnergyAllocationMapper allocationMapper;
    private final AllocationService allocationService;
    private final BillMapper billMapper;

    @Operation(summary = "分页查询月度公用事业账单")
    @GetMapping("/page")
    public Result<PageResult<UtilityBill>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String period,
                                                @RequestParam(required = false) String energyType,
                                                @RequestParam(required = false) String status) {
        LambdaQueryWrapper<UtilityBill> qw = new LambdaQueryWrapper<UtilityBill>()
                .eq(StringUtils.hasText(period), UtilityBill::getPeriod, period)
                .eq(StringUtils.hasText(energyType), UtilityBill::getEnergyType, energyType)
                .eq(StringUtils.hasText(status), UtilityBill::getStatus, status)
                .orderByDesc(UtilityBill::getPeriod).orderByDesc(UtilityBill::getId);
        IPage<UtilityBill> p = utilityBillMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "新增月度公用事业账单(录发票)")
    @OperationLog(module = "公用事业账单", action = "新增")
    @PostMapping
    public Result<Long> add(@RequestBody UtilityBill bill) {
        validate(bill);
        bill.setStatus(UtilityBill.ST_DRAFT);
        try {
            utilityBillMapper.insert(bill);
        } catch (DuplicateKeyException e) {
            throw new BizException("该园区 " + bill.getPeriod() + " 的" + bill.getEnergyType() + "费账单已存在,请勿重复录入");
        }
        return Result.ok(bill.getId());
    }

    @Operation(summary = "修改月度公用事业账单")
    @OperationLog(module = "公用事业账单", action = "修改")
    @PutMapping
    public Result<Void> update(@RequestBody UtilityBill bill) {
        UtilityBill existed = requireDraft(bill.getId());
        validate(bill);
        // 状态由确认/撤销流程控制,不让普通编辑绕过去
        bill.setStatus(existed.getStatus());
        utilityBillMapper.updateById(bill);
        return Result.ok();
    }

    @Operation(summary = "删除月度公用事业账单(已出账的不能删)")
    @OperationLog(module = "公用事业账单", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireDraft(id);
        allocationMapper.delete(new LambdaQueryWrapper<EnergyAllocation>()
                .eq(EnergyAllocation::getUtilityBillId, id));
        utilityBillMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按《附件二》公式测算分摊(可反复重跑,幂等)")
    @PreAuthorize("hasAuthority('energy:allocation:calc')")
    @OperationLog(module = "公用事业账单", action = "测算分摊")
    @PostMapping("/{id}/calculate")
    public Result<AllocationService.AllocationSummary> calculate(@PathVariable Long id) {
        return Result.ok(allocationService.calculate(id));
    }

    @Operation(summary = "查已保存的分摊明细(不重算)")
    @GetMapping("/{id}/allocations")
    public Result<List<EnergyAllocation>> allocations(@PathVariable Long id) {
        return Result.ok(allocationService.listSaved(id));
    }

    /**
     * 确认出账:给每个有租户的分表生成一张能源费账单。
     *
     * <p>幂等靠 fin_bill 的唯一索引:billing_key = allocation:{分摊行id},
     * 重复确认不会出两张。物业公司自己的表不出账(那是园区内部成本)。</p>
     */
    @Operation(summary = "确认出账:按分摊结果给每个租户生成能源费账单")
    @PreAuthorize("hasAuthority('energy:allocation:calc')")
    @OperationLog(module = "公用事业账单", action = "确认出账")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> confirm(@PathVariable Long id) {
        UtilityBill bill = utilityBillMapper.selectById(id);
        if (bill == null) {
            throw new BizException("月度公用事业账单不存在: " + id);
        }
        List<EnergyAllocation> items = allocationMapper.selectList(
                new LambdaQueryWrapper<EnergyAllocation>().eq(EnergyAllocation::getUtilityBillId, id));
        if (items.isEmpty()) {
            throw new BizException("还没有分摊结果,请先点「测算分摊」");
        }
        YearMonth ym = YearMonth.parse(bill.getPeriod());
        int inserted = 0;
        int skipped = 0;
        for (EnergyAllocation a : items) {
            // 物业公司分表不出账;没绑到租户的分表也出不了账(不知道该向谁收)
            if (!"TENANT".equals(a.getMeterRole()) || a.getTenantRefId() == null
                    || a.getTotalFee() == null || a.getTotalFee().compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }
            String key = "allocation:" + a.getId();
            Bill existed = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getBillingKey, key).last("limit 1"));
            if (existed != null) {
                a.setBillId(existed.getId());
                allocationMapper.updateById(a);
                skipped++;
                continue;
            }
            Bill b = new Bill();
            b.setCode("UT" + bill.getEnergyType() + ym.toString().replace("-", "") + "-" + a.getMeterId());
            b.setBillingKey(key);
            b.setTenantRefId(a.getTenantRefId());
            b.setProjectId(bill.getProjectId());
            b.setDirection(1);
            b.setFeeType(FEE_TYPE_ENERGY);
            b.setSource("抄表");
            b.setStatus(3);
            b.setAmount(a.getTotalFee());
            b.setPaidAmount(BigDecimal.ZERO);
            b.setLateFee(BigDecimal.ZERO);
            b.setPeriodStart(ym.atDay(1));
            b.setPeriodEnd(ym.atEndOfMonth());
            b.setDueDate(ym.atEndOfMonth().plusMonths(1));
            b.setInvoiceStatus(0);
            b.setRemark("%s %s：自用 %s(¥%s) + 公摊 %s(系数 %s，¥%s)".formatted(
                    bill.getPeriod(), bill.getEnergyType(),
                    a.getOwnUsage().toPlainString(), a.getOwnFee().toPlainString(),
                    a.getAllocUsage().toPlainString(), a.getAllocCoefficient().toPlainString(),
                    a.getAllocFee().toPlainString()));
            billMapper.insert(b);
            a.setBillId(b.getId());
            allocationMapper.updateById(a);
            inserted++;
        }
        bill.setStatus(UtilityBill.ST_CONFIRMED);
        utilityBillMapper.updateById(bill);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inserted", inserted);
        m.put("skipped", skipped);
        return Result.ok(m);
    }

    @Operation(summary = "撤销确认(已收款的账单不动,只把账期放回草稿以便重算)")
    @PreAuthorize("hasAuthority('energy:allocation:calc')")
    @OperationLog(module = "公用事业账单", action = "撤销确认")
    @PostMapping("/{id}/revoke")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> revoke(@PathVariable Long id) {
        UtilityBill bill = utilityBillMapper.selectById(id);
        if (bill == null) {
            throw new BizException("月度公用事业账单不存在: " + id);
        }
        List<EnergyAllocation> items = allocationMapper.selectList(
                new LambdaQueryWrapper<EnergyAllocation>().eq(EnergyAllocation::getUtilityBillId, id));
        int removed = 0;
        int kept = 0;
        for (EnergyAllocation a : items) {
            if (a.getBillId() == null) {
                continue;
            }
            Bill b = billMapper.selectById(a.getBillId());
            // 已经收过钱的账单不能撤:撤了就对不上账,让用户走红冲
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                kept++;
                continue;
            }
            if (b != null) {
                billMapper.deleteById(b.getId());
            }
            a.setBillId(null);
            allocationMapper.updateById(a);
            removed++;
        }
        if (kept > 0) {
            throw new BizException("有 " + kept + " 张账单已收款,不能撤销;请先在收款记录里红冲后重试");
        }
        bill.setStatus(UtilityBill.ST_DRAFT);
        utilityBillMapper.updateById(bill);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("removed", removed);
        return Result.ok(m);
    }

    private UtilityBill requireDraft(Long id) {
        UtilityBill existed = utilityBillMapper.selectById(id);
        if (existed == null) {
            throw new BizException("月度公用事业账单不存在: " + id);
        }
        if (UtilityBill.ST_CONFIRMED.equals(existed.getStatus())) {
            throw new BizException("该账期已确认出账,请先撤销确认再改动");
        }
        return existed;
    }

    private void validate(UtilityBill bill) {
        if (bill == null || !StringUtils.hasText(bill.getPeriod()) || !StringUtils.hasText(bill.getEnergyType())) {
            throw new BizException("账期与能源类型不能为空");
        }
        try {
            YearMonth.parse(bill.getPeriod());
        } catch (Exception e) {
            throw new BizException("账期格式应为 yyyy-MM,如 " + YearMonth.from(LocalDate.now()));
        }
        if (bill.getInvoiceUsage() == null || bill.getInvoiceUsage().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("发票总用量必须大于 0");
        }
        if (bill.getInvoiceAmountExTax() == null || bill.getInvoiceAmountExTax().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("发票不含税总额不能为负");
        }
        if (bill.getTaxRate() == null || bill.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("税率不能为负");
        }
    }
}
