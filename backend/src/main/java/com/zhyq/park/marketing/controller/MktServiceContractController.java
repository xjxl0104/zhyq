package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktContractWarehouse;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceContractVersion;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktContractWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.service.MktLockService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "全民营销-云仓服务合同")
@RestController
@RequestMapping("/crm/marketing/contract")
@RequiredArgsConstructor
public class MktServiceContractController {

    private final MktServiceContractMapper contractMapper;
    private final MktServiceContractVersionMapper versionMapper;
    private final MktContractWarehouseMapper contractWarehouseMapper;
    private final MktWarehouseMapper warehouseMapper;
    private final CustomerMapper customerMapper;
    private final MktServiceContractService contractService;
    private final MktWarehouseOnboardingService onboardingService;
    private final MktLockService lockService;
    private final BizSettings bizSettings;

    @Operation(summary = "分页(附客户名/云仓名)")
    @PreAuthorize("hasAuthority('crm:marketing:contract:query')")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Integer signMode,
                                                        @RequestParam(required = false) Long customerId,
                                                        @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktServiceContract> qw = new LambdaQueryWrapper<MktServiceContract>()
                .like(StringUtils.hasText(keyword), MktServiceContract::getContractNo, keyword)
                .eq(status != null, MktServiceContract::getStatus, status)
                .eq(signMode != null, MktServiceContract::getSignMode, signMode)
                .eq(customerId != null, MktServiceContract::getCustomerId, customerId)
                .eq(projectId != null, MktServiceContract::getProjectId, projectId)
                .orderByDesc(MktServiceContract::getId);
        IPage<MktServiceContract> p = contractMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(this::enrich).collect(Collectors.toList())));
    }

    @Operation(summary = "详情")
    @PreAuthorize("hasAuthority('crm:marketing:contract:query')")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MktServiceContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException("合同不存在");
        return Result.ok(enrich(c));
    }

    @Operation(summary = "版本快照")
    @PreAuthorize("hasAuthority('crm:marketing:contract:query')")
    @GetMapping("/{id}/versions")
    public Result<List<MktServiceContractVersion>> versions(@PathVariable Long id) {
        return Result.ok(versionMapper.selectList(new LambdaQueryWrapper<MktServiceContractVersion>()
                .eq(MktServiceContractVersion::getContractId, id).orderByDesc(MktServiceContractVersion::getVerNo)));
    }

    @Operation(summary = "起草(校验云仓可承接,落 crm_contract_warehouse)")
    @PreAuthorize("hasAuthority('crm:marketing:contract:edit')")
    @PostMapping
    @Transactional
    public Result<MktServiceContract> create(@RequestBody MktServiceContract draft) {
        MktWarehouse w = warehouseMapper.selectById(draft.getWarehouseId());
        if (!onboardingService.canAcceptCustomers(w)) {
            throw new BizException("承接云仓必须是已上线且 ERP 已联通");
        }
        MktServiceContract c = contractService.createDraft(draft);
        MktContractWarehouse cw = new MktContractWarehouse();
        cw.setContractId(c.getId());
        cw.setWarehouseId(w.getId());
        cw.setIsPrimary(1);
        cw.setEffectiveFrom(c.getStartDate() == null ? LocalDate.now() : c.getStartDate());
        cw.setProjectId(c.getProjectId());
        contractWarehouseMapper.insert(cw);
        return Result.ok(c);
    }

    @Operation(summary = "编辑草稿")
    @PreAuthorize("hasAuthority('crm:marketing:contract:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody MktServiceContract c) {
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, c.getId())
                .eq(MktServiceContract::getStatus, MktServiceContractService.ST_DRAFT)
                .set(MktServiceContract::getWarehouseId, c.getWarehouseId())
                .set(MktServiceContract::getServiceType, c.getServiceType())
                .set(MktServiceContract::getFeeModel, c.getFeeModel())
                .set(MktServiceContract::getPriceTable, c.getPriceTable())
                .set(MktServiceContract::getDeposit, c.getDeposit())
                .set(MktServiceContract::getStartDate, c.getStartDate())
                .set(MktServiceContract::getEndDate, c.getEndDate())
                .set(MktServiceContract::getPayCycle, c.getPayCycle())
                .set(MktServiceContract::getTemplateId, c.getTemplateId())
                .set(MktServiceContract::getSignMode, c.getSignMode())
                .set(MktServiceContract::getRemark, c.getRemark()));
        if (updated == 0) throw new BizException("只有草稿可编辑");
        return Result.ok();
    }

    @Operation(summary = "提交审核") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) { contractService.submit(id); return Result.ok(); }

    @Operation(summary = "审核") @PreAuthorize("hasAuthority('crm:marketing:contract:audit')") @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        contractService.audit(id, Boolean.TRUE.equals(body.get("pass")), (String) body.get("reason")); return Result.ok();
    }

    @Operation(summary = "线下签署 → 生效(佣金冻结生成、首期账单)") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/sign-offline")
    public Result<Void> signOffline(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.signOffline(id, body.get("files"));
        markDeal(id);
        return Result.ok();
    }

    @Operation(summary = "直签备案生效") @PreAuthorize("hasAuthority('crm:marketing:contract:audit')") @PostMapping("/{id}/effect-direct")
    public Result<Void> effectDirect(@PathVariable Long id) { contractService.effectDirect(id); markDeal(id); return Result.ok(); }

    @Operation(summary = "首期款到账 → 履约中(手动)") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/perform")
    public Result<Void> perform(@PathVariable Long id) { contractService.startPerforming(id); return Result.ok(); }

    @Operation(summary = "发起变更") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/amend")
    public Result<Void> amend(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.amend(id, body.getOrDefault("changeNote", body.get("reason"))); return Result.ok();
    }

    @Operation(summary = "变更完成") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/amend-done")
    public Result<Void> amendDone(@PathVariable Long id) { contractService.amendDone(id); return Result.ok(); }

    @Operation(summary = "续签") @PreAuthorize("hasAuthority('crm:marketing:contract:edit')") @PostMapping("/{id}/renew")
    public Result<Void> renew(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.renew(id, LocalDate.parse(body.get("startDate")), LocalDate.parse(body.get("endDate"))); return Result.ok();
    }

    @Operation(summary = "终止(clawback_days 内扣回签约奖)") @PreAuthorize("hasAuthority('crm:marketing:contract:audit')") @PostMapping("/{id}/terminate")
    public Result<Void> terminate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.terminate(id, body.get("reason"), bizSettings.getInt("marketing", "clawback_days", 90)); return Result.ok();
    }

    @Operation(summary = "作废") @PreAuthorize("hasAuthority('crm:marketing:contract:audit')") @PostMapping("/{id}/void")
    public Result<Void> voidContract(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.voidContract(id, body.get("reason")); return Result.ok();
    }

    /** 合同生效 → 该客户的锁定置为已成交(归属固定到合同期)。 */
    private void markDeal(Long contractId) {
        MktServiceContract c = contractMapper.selectById(contractId);
        if (c != null) lockService.markDeal(c.getCustomerId());
    }

    private Map<String, Object> enrich(MktServiceContract c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId()); m.put("contractNo", c.getContractNo()); m.put("signMode", c.getSignMode());
        m.put("customerId", c.getCustomerId()); m.put("warehouseId", c.getWarehouseId()); m.put("partnerId", c.getPartnerId());
        m.put("grade", c.getGrade()); m.put("serviceType", c.getServiceType()); m.put("feeModel", c.getFeeModel());
        m.put("priceTable", c.getPriceTable()); m.put("deposit", c.getDeposit()); m.put("startDate", c.getStartDate());
        m.put("endDate", c.getEndDate()); m.put("payCycle", c.getPayCycle()); m.put("status", c.getStatus());
        m.put("signedAt", c.getSignedAt()); m.put("effectiveAt", c.getEffectiveAt()); m.put("contractVersion", c.getContractVersion());
        m.put("templateId", c.getTemplateId()); m.put("files", c.getFiles()); m.put("auditReason", c.getAuditReason());
        m.put("terminateReason", c.getTerminateReason()); m.put("remark", c.getRemark()); m.put("createTime", c.getCreateTime());
        Customer cu = c.getCustomerId() == null ? null : customerMapper.selectById(c.getCustomerId());
        m.put("customerName", cu == null ? null : cu.getName());
        MktWarehouse w = c.getWarehouseId() == null ? null : warehouseMapper.selectById(c.getWarehouseId());
        m.put("warehouseName", w == null ? null : w.getName());
        return m;
    }
}
