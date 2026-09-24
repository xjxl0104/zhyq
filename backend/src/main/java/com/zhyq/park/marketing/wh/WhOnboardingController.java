package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWarehouseFileService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wh/v1")
@RequiredArgsConstructor
public class WhOnboardingController {
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseOnboardingMapper stepMapper;
    private final MktWarehouseOnboardingService onboardingService;
    private final MktAuditService auditService;
    private final MktWarehouseFileService files;
    private final ObjectMapper json;

    @GetMapping("/apply")
    public Result<MktWarehouse> apply() { return Result.ok(WhAuthContext.requireWarehouse(warehouseMapper)); }

    /** Approval locks merchant-editable qualification fields. Neither identity nor state comes from the body. */
    @RequestMapping(value = "/apply", method = {RequestMethod.POST, RequestMethod.PUT})
    @Transactional
    public Result<MktWarehouse> saveApply(@RequestBody MktWarehouse body) {
        Long id = WhAuthContext.currentWarehouseId();
        MktWarehouse before = WhAuthContext.requireWarehouse(warehouseMapper, id);
        MktWarehouseOnboardingService.validateProfile(body);
        int changed = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, id).in(MktWarehouse::getJoinStatus, 1, 2)
                .set(MktWarehouse::getName, body.getName().trim()).set(MktWarehouse::getRegion, body.getRegion().trim())
                .set(MktWarehouse::getAddress, body.getAddress().trim()).set(MktWarehouse::getContact, body.getContact().trim())
                .set(MktWarehouse::getPhone, body.getPhone()).set(MktWarehouse::getAreaSqm, body.getAreaSqm())
                .set(MktWarehouse::getDailyCapacity, body.getDailyCapacity()).set(MktWarehouse::getCategories, body.getCategories())
                .set(MktWarehouse::getRemark, body.getRemark()).setSql("version = version + 1"));
        if (changed != 1) throw new BizException("资质已审核，资料修改请联系园区运营；刷新后查看最新状态");
        auditService.log("warehouse.apply.update", "warehouse", id, "商家更新待审核资料");
        before.setName(body.getName().trim()); before.setRegion(body.getRegion().trim()); before.setAddress(body.getAddress().trim());
        before.setContact(body.getContact().trim()); before.setPhone(body.getPhone()); before.setAreaSqm(body.getAreaSqm());
        before.setDailyCapacity(body.getDailyCapacity()); before.setCategories(body.getCategories()); before.setRemark(body.getRemark());
        return Result.ok(before);
    }

    @PostMapping("/apply/submit")
    public Result<Void> submit() { onboardingService.resubmit(WhAuthContext.currentWarehouseId()); return Result.ok(); }

    @GetMapping("/onboarding")
    public Result<List<MktWarehouseOnboarding>> onboarding() {
        Long id = WhAuthContext.currentWarehouseId(); WhAuthContext.requireWarehouse(warehouseMapper, id);
        return Result.ok(stepMapper.selectList(onboardingService.stepsOf(id)));
    }

    @PostMapping("/apply/attachments")
    @Transactional
    public Result<Void> attachments(@RequestBody Map<String, Object> body) {
        Long id = WhAuthContext.currentWarehouseId();
        Object refs = body.get("attachments");
        if (refs == null) throw new BizException("附件不能为空");
        String value;
        try { value = files.validateReferences(refs instanceof String s ? s : json.writeValueAsString(refs), id); }
        catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("附件格式不正确"); }
        int locked = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, id).in(MktWarehouse::getJoinStatus, 1, 2).setSql("version = version + 1"));
        if (locked != 1) throw new BizException("资质已审核，附件修改请联系园区运营");
        int updated = stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>()
                .eq(MktWarehouseOnboarding::getWarehouseId, id).eq(MktWarehouseOnboarding::getStep, 1)
                .set(MktWarehouseOnboarding::getAttachments, value));
        if (updated != 1) throw new BizException("申请记录不存在，请联系园区运营");
        auditService.log("warehouse.apply.attachments", "warehouse", id, "商家更新已上传的申请附件");
        return Result.ok();
    }
}
