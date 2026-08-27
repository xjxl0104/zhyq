package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.pur.entity.PurPlan;
import com.zhyq.park.pur.mapper.PurPlanMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购管理-采购计划")
@RestController
@RequestMapping("/pur/plan")
@RequiredArgsConstructor
public class PurPlanController {

    private final PurPlanMapper planMapper;

    @Operation(summary = "分页查询采购计划(planType:1年度 2月度 3临时)")
    @PreAuthorize("hasAuthority('pur:plan:query')")
    @GetMapping("/page")
    public Result<PageResult<PurPlan>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Integer planType,
                                            @RequestParam(required = false) String period,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<PurPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(planType != null, PurPlan::getPlanType, planType)
          .like(StringUtils.hasText(period), PurPlan::getPeriod, period)
          .like(StringUtils.hasText(title), PurPlan::getTitle, title)
          .eq(status != null, PurPlan::getStatus, status)
          .orderByDesc(PurPlan::getId);
        IPage<PurPlan> p = planMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "采购计划下拉列表(供采购申请关联)")
    @PreAuthorize("hasAuthority('pur:plan:query')")
    @GetMapping("/list")
    public Result<List<PurPlan>> list(@RequestParam(required = false) Integer planType) {
        return Result.ok(planMapper.selectList(new LambdaQueryWrapper<PurPlan>()
                .eq(planType != null, PurPlan::getPlanType, planType)
                .orderByDesc(PurPlan::getId)));
    }

    @Operation(summary = "采购计划详情")
    @PreAuthorize("hasAuthority('pur:plan:query')")
    @GetMapping("/{id}")
    public Result<PurPlan> get(@PathVariable Long id) {
        return Result.ok(planMapper.selectById(id));
    }

    @Operation(summary = "新增采购计划")
    @PreAuthorize("hasAuthority('pur:plan:add')")
    @PostMapping
    public Result<Long> add(@RequestBody PurPlan plan) {
        plan.setId(null);
        // status 一律服务端定为草稿:入参带 status 就能直接建出"已完成"计划。
        // tenantId / 审计字段由 MyMetaObjectHandler 填充, 不接受入参。
        plan.setStatus(1);
        plan.setTenantId(null);
        planMapper.insert(plan);
        return Result.ok(plan.getId());
    }

    /**
     * 白名单字段更新。原实现用 updateById(plan) 全字段更新, 入参里带
     * tenantId / deleted / createBy 就会一并写库(BaseEntity 那些字段没有
     * @JsonIgnore, MetaObjectHandler 也只在 null 时填充), 可越权改归属或软删。
     * 口径与 PurRequestService.update() 保持一致。
     */
    @Operation(summary = "编辑采购计划")
    @PreAuthorize("hasAuthority('pur:plan:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody PurPlan plan) {
        if (plan.getId() == null) {
            throw new BizException("缺少采购计划 id");
        }
        int updated = planMapper.update(null, new LambdaUpdateWrapper<PurPlan>()
                .eq(PurPlan::getId, plan.getId())
                .set(PurPlan::getTitle, plan.getTitle())
                .set(PurPlan::getPlanType, plan.getPlanType())
                .set(PurPlan::getPeriod, plan.getPeriod())
                .set(PurPlan::getDepartment, plan.getDepartment())
                .set(PurPlan::getApplicant, plan.getApplicant())
                .set(PurPlan::getBudgetAmount, plan.getBudgetAmount())
                .set(PurPlan::getStatus, plan.getStatus())
                .set(PurPlan::getRemark, plan.getRemark()));
        if (updated == 0) {
            throw new BizException("采购计划不存在或已删除");
        }
        return Result.ok();
    }

    @Operation(summary = "删除采购计划")
    @PreAuthorize("hasAuthority('pur:plan:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        planMapper.deleteById(id);
        return Result.ok();
    }
}
