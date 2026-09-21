package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseOnboarding;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseOnboardingMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "云仓端-加盟申请")
@RestController
@RequestMapping("/wh/v1")
@RequiredArgsConstructor
public class WhOnboardingController {
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseOnboardingMapper stepMapper;
    private final MktWarehouseOnboardingService onboardingService;
    private final MktAuditService auditService;

    @Operation(summary = "查看当前云仓申请资料")
    @GetMapping("/apply")
    public Result<MktWarehouse> apply() { return Result.ok(WhAuthContext.requireWarehouse(warehouseMapper)); }

    @Operation(summary = "提交/修改当前云仓申请资料")
    @PostMapping("/apply")
    @PutMapping("/apply")
    public Result<MktWarehouse> saveApply(@RequestBody MktWarehouse body) {
        Long id = WhAuthContext.currentWarehouseId();
        MktWarehouse before = WhAuthContext.requireWarehouse(warehouseMapper, id);
        int status = before.getJoinStatus() == null ? MktWarehouseOnboardingService.JS_APPLIED : before.getJoinStatus();
        warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, id)
                .set(MktWarehouse::getName, body.getName()).set(MktWarehouse::getRegion, body.getRegion())
                .set(MktWarehouse::getAddress, body.getAddress()).set(MktWarehouse::getContact, body.getContact())
                .set(MktWarehouse::getPhone, body.getPhone()).set(MktWarehouse::getAreaSqm, body.getAreaSqm())
                .set(MktWarehouse::getDailyCapacity, body.getDailyCapacity()).set(MktWarehouse::getCategories, body.getCategories())
                .set(MktWarehouse::getRemark, body.getRemark()).set(MktWarehouse::getJoinStatus, status));
        auditService.log("warehouse.apply.update", "warehouse", id, "云仓端更新申请", before, body);
        before.setName(body.getName()); before.setRegion(body.getRegion()); before.setAddress(body.getAddress()); before.setContact(body.getContact());
        before.setPhone(body.getPhone()); before.setAreaSqm(body.getAreaSqm()); before.setDailyCapacity(body.getDailyCapacity()); before.setCategories(body.getCategories()); before.setRemark(body.getRemark());
        return Result.ok(before);
    }

    @Operation(summary = "加盟进度五步时间线")
    @GetMapping("/onboarding")
    public Result<List<MktWarehouseOnboarding>> onboarding() {
        Long id = WhAuthContext.currentWarehouseId();
        return Result.ok(stepMapper.selectList(onboardingService.stepsOf(id)));
    }

    @Operation(summary = "上传申请附件引用")
    @PostMapping("/apply/attachments")
    public Result<Void> attachments(@RequestBody Map<String, Object> body) {
        Long id = WhAuthContext.currentWarehouseId(); WhAuthContext.requireWarehouse(warehouseMapper, id);
        Object refs = body.get("attachments");
        if (refs == null) throw new BizException("附件引用不能为空");
        // Store references only; binary content is handled by file service and audited separately.
        String value = refs instanceof String s ? s : refs.toString();
        stepMapper.update(null, new LambdaUpdateWrapper<MktWarehouseOnboarding>().eq(MktWarehouseOnboarding::getWarehouseId, id)
                .eq(MktWarehouseOnboarding::getStep, MktWarehouseOnboardingService.STEP_APPLY).set(MktWarehouseOnboarding::getAttachments, value));
        auditService.log("warehouse.apply.attachments", "warehouse", id, "云仓端上传申请附件引用");
        return Result.ok();
    }
}
