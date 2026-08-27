package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.pur.entity.PurRequest;
import com.zhyq.park.pur.mapper.PurRequestMapper;
import com.zhyq.park.pur.service.PurRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "采购管理-采购申请")
@RestController
@RequestMapping("/pur/request")
@RequiredArgsConstructor
public class PurRequestController {

    private final PurRequestMapper requestMapper;
    private final PurRequestService requestService;

    @Operation(summary = "分页查询采购申请")
    @PreAuthorize("hasAuthority('pur:request:query')")
    @GetMapping("/page")
    public Result<PageResult<PurRequest>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String title,
                                               @RequestParam(required = false) String supplier,
                                               @RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<PurRequest> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), PurRequest::getTitle, title)
          .like(StringUtils.hasText(supplier), PurRequest::getSupplier, supplier)
          .eq(planId != null, PurRequest::getPlanId, planId)
          .eq(status != null, PurRequest::getStatus, status)
          .orderByDesc(PurRequest::getId);
        IPage<PurRequest> p = requestMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "采购申请详情(含明细)")
    @PreAuthorize("hasAuthority('pur:request:query')")
    @GetMapping("/{id}")
    public Result<PurRequest> get(@PathVariable Long id) {
        return Result.ok(requestService.detail(id));
    }

    @Operation(summary = "新增采购申请(含明细,自动生成单号、汇总金额,初始为草稿)")
    @PreAuthorize("hasAuthority('pur:request:add')")
    @PostMapping
    public Result<Long> add(@RequestBody PurRequest request) {
        return Result.ok(requestService.create(request));
    }

    @Operation(summary = "编辑采购申请(仅草稿/已驳回可编辑,整单替换明细)")
    @PreAuthorize("hasAuthority('pur:request:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody PurRequest request) {
        requestService.update(request);
        return Result.ok();
    }

    @Operation(summary = "提交审批(草稿/已驳回→审批中,发起审批链)")
    @PreAuthorize("hasAuthority('pur:request:submit')")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        requestService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "标记完成(到货入库确认)")
    @PreAuthorize("hasAuthority('pur:request:complete')")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        requestService.complete(id);
        return Result.ok();
    }

    @Operation(summary = "取消采购申请")
    @PreAuthorize("hasAuthority('pur:request:cancel')")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        requestService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "删除采购申请(审批中/已通过/已完成不可删)")
    @PreAuthorize("hasAuthority('pur:request:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        requestService.remove(id);
        return Result.ok();
    }
}
