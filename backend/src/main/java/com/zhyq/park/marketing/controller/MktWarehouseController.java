package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWarehouseFileService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "全民营销-云仓与加盟")
@RestController
@RequestMapping("/crm/marketing/warehouse")
@RequiredArgsConstructor
public class MktWarehouseController {

    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseOnboardingMapper stepMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktWarehouseOnboardingService onboardingService;
    private final MktAuditService auditService;
    private final MktWarehouseFileService files;

    @Operation(summary = "分页;inProgress=1 只看未上线(加盟申请页)")
    @PreAuthorize("hasAuthority('crm:marketing:warehouse:query')")
    @GetMapping("/page")
    public Result<PageResult<MktWarehouse>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer joinStatus,
                                                 @RequestParam(required = false) Integer inProgress,
                                                 @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<MktWarehouse> qw = new LambdaQueryWrapper<MktWarehouse>()
                .and(StringUtils.hasText(keyword), w -> w.like(MktWarehouse::getName, keyword).or().like(MktWarehouse::getCode, keyword))
                .eq(joinStatus != null, MktWarehouse::getJoinStatus, joinStatus)
                .in(Integer.valueOf(1).equals(inProgress), MktWarehouse::getJoinStatus,
                        MktWarehouseOnboardingService.JS_APPLIED, MktWarehouseOnboardingService.JS_QUALIFYING,
                        MktWarehouseOnboardingService.JS_ERP_CONNECTING, MktWarehouseOnboardingService.JS_PENDING_AGREEMENT)
                .and(projectId != null, q -> q.eq(MktWarehouse::getProjectId, projectId).or().isNull(MktWarehouse::getProjectId))
                .orderByDesc(MktWarehouse::getId);
        IPage<MktWarehouse> p = warehouseMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "可承接客户的云仓(已上线且 ERP 联通)")
    @PreAuthorize("hasAuthority('crm:marketing:warehouse:query')")
    @GetMapping("/online")
    public Result<List<MktWarehouse>> online() {
        return Result.ok(warehouseMapper.selectList(new LambdaQueryWrapper<MktWarehouse>()
                .eq(MktWarehouse::getJoinStatus, MktWarehouseOnboardingService.JS_ONLINE)
                .and(q -> q.eq(MktWarehouse::getOrderMode, "manual").or().eq(MktWarehouse::getErpStatus, MktWarehouseOnboardingService.ERP_LIVE))
                .select(MktWarehouse::getId, MktWarehouse::getCode, MktWarehouse::getName, MktWarehouse::getRegion)));
    }

    @Operation(summary = "详情") @PreAuthorize("hasAuthority('crm:marketing:warehouse:query')") @GetMapping("/{id}")
    public Result<MktWarehouse> get(@PathVariable Long id) {
        MktWarehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BizException("云仓不存在");
        return Result.ok(w);
    }

    @Operation(summary = "加盟步骤时间线") @PreAuthorize("hasAuthority('crm:marketing:onboarding:query')") @GetMapping("/{id}/steps")
    public Result<List<MktWarehouseOnboarding>> steps(@PathVariable Long id) {
        return Result.ok(stepMapper.selectList(onboardingService.stepsOf(id)));
    }

    @Operation(summary = "新增云仓 = 提交加盟申请") @PreAuthorize("hasAuthority('crm:marketing:warehouse:edit')") @PostMapping
    public Result<MktWarehouse> apply(@RequestBody MktWarehouse req) {
        // 白名单:只接收资料字段。contactOpenid 是阶段 C 云仓登录身份,由 WhAuthService 绑定,禁止后台申请时指定
        // (否则可填任意 openid 登成该云仓);joinStatus/erpStatus/contractFile 等状态字段由服务端推进。
        MktWarehouse w = new MktWarehouse();
        w.setCode(req.getCode());
        w.setName(req.getName());
        w.setRegion(req.getRegion());
        w.setAddress(req.getAddress());
        w.setContact(req.getContact());
        w.setPhone(req.getPhone());
        w.setAreaSqm(req.getAreaSqm());
        w.setDailyCapacity(req.getDailyCapacity());
        w.setCategories(req.getCategories());
        w.setSettleCycle(req.getSettleCycle());
        w.setFeeModel(req.getFeeModel());
        w.setPlatformFeeModel(req.getPlatformFeeModel());
        w.setRemark(req.getRemark());
        w.setProjectId(req.getProjectId());
        validateRates(w);
        return Result.ok(onboardingService.apply(w));
    }

    @Operation(summary = "编辑资料(不改状态)") @PreAuthorize("hasAuthority('crm:marketing:warehouse:edit')") @PutMapping
    public Result<Void> update(@RequestBody MktWarehouse w) {
        MktWarehouseOnboardingService.validateProfile(w);
        validateRates(w);
        MktWarehouse before = warehouseMapper.selectById(w.getId());
        if (before == null) throw new BizException("云仓不存在");
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, w.getId()).ne(MktWarehouse::getJoinStatus, 7)
                .set(MktWarehouse::getName, w.getName()).set(MktWarehouse::getRegion, w.getRegion())
                .set(MktWarehouse::getAddress, w.getAddress()).set(MktWarehouse::getContact, w.getContact())
                .set(MktWarehouse::getPhone, w.getPhone()).set(MktWarehouse::getAreaSqm, w.getAreaSqm())
                .set(MktWarehouse::getDailyCapacity, w.getDailyCapacity()).set(MktWarehouse::getCategories, w.getCategories())
                .set(MktWarehouse::getSettleCycle, w.getSettleCycle()).set(MktWarehouse::getFeeModel,
                        StringUtils.hasText(w.getFeeModel()) ? w.getFeeModel() : null)
                .set(MktWarehouse::getPlatformFeeModel, w.getPlatformFeeModel()).set(MktWarehouse::getRemark, w.getRemark())
                .setSql("version = version + 1"));
        if (updated != 1) throw new BizException("云仓状态已变化，请刷新重试");
        auditService.log("warehouse.update", "warehouse", w.getId(), null, before, w);
        return Result.ok();
    }

    @Operation(summary = "资质审核通过") @PreAuthorize("hasAuthority('crm:marketing:onboarding:audit')") @PostMapping("/{id}/qualify/pass")
    public Result<Void> passQualification(@PathVariable Long id, @RequestBody QualificationReview body) {
        onboardingService.passQualification(id, body.version()); return Result.ok();
    }
    public record QualificationReview(Integer version) {}

    @Operation(summary = "资质驳回") @PreAuthorize("hasAuthority('crm:marketing:onboarding:audit')") @PostMapping("/{id}/qualify/reject")
    public Result<Void> rejectQualification(@PathVariable Long id, @RequestBody Map<String, String> body) {
        onboardingService.rejectQualification(id, body.get("reason")); return Result.ok();
    }

    @Operation(summary = "人工标记 ERP 已联通(阶段 A)") @PreAuthorize("hasAuthority('crm:marketing:onboarding:audit')") @PostMapping("/{id}/erp/mark-connected")
    public Result<Void> markErp(@PathVariable Long id) { onboardingService.markErpConnected(id, MktAuditService.currentOperator()); return Result.ok(); }

    @Operation(summary = "采用人工导入出库单")
    @PreAuthorize("hasAuthority('crm:marketing:onboarding:audit')")
    @PostMapping("/{id}/order-mode/manual")
    public Result<Void> useManual(@PathVariable Long id) { onboardingService.useManualOrders(id); return Result.ok(); }

    @Operation(summary = "上传加盟协议 → 上线") @PreAuthorize("hasAuthority('crm:marketing:onboarding:audit')") @PostMapping("/{id}/agreement")
    public Result<Void> signAgreement(@PathVariable Long id, @RequestBody Map<String, String> body) {
        files.requireReference(body.get("contractFile"), id);
        onboardingService.signAgreement(id, body.get("contractFile")); return Result.ok();
    }

    @Operation(summary = "暂停") @PreAuthorize("hasAuthority('crm:marketing:warehouse:audit')") @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id, @RequestBody Map<String, String> body) { onboardingService.pause(id, body.get("reason")); return Result.ok(); }

    @Operation(summary = "恢复") @PreAuthorize("hasAuthority('crm:marketing:warehouse:audit')") @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) { onboardingService.resume(id); return Result.ok(); }

    @Operation(summary = "退出(在服客户须为 0)") @PreAuthorize("hasAuthority('crm:marketing:warehouse:audit')") @PostMapping("/{id}/exit")
    public Result<Void> exit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        long serving = contractMapper.selectCount(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getWarehouseId, id)
                .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING));
        if (serving > 0) throw new BizException("该云仓还有 " + serving + " 份生效中的服务合同,不能退出");
        onboardingService.exit(id, body.get("reason"));
        return Result.ok();
    }
    private static void validateRates(MktWarehouse w) {
        if (w.getSettleCycle() != null && (w.getSettleCycle() < 1 || w.getSettleCycle() > 3)) throw new BizException("请选择有效结算周期");
        if (!StringUtils.hasText(w.getFeeModel())) return;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(w.getFeeModel());
            if (!node.isObject() || node.isEmpty()) throw new BizException("请填写云仓约定单价，或清空未约定费率");
            var keys = node.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next(); var value = node.get(key);
                if (!java.util.Set.of("perOrder", "perItem").contains(key) || !value.isNumber()
                        || value.decimalValue().signum() < 0 || value.decimalValue().scale() > 2
                        || value.decimalValue().compareTo(new java.math.BigDecimal("99999999.99")) > 0)
                    throw new BizException("云仓单价须为非负金额，最多两位小数");
            }
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("云仓费率格式无效"); }
    }
}
