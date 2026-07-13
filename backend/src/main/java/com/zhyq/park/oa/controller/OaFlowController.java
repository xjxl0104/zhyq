package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.OaFlow;
import com.zhyq.park.oa.mapper.OaFlowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 流程管理(oa_flow,定义管理,不做运行引擎;审批实例走 biz_approval)
 */
@Tag(name = "办公管理-流程")
@RestController
@RequestMapping("/oa/flow")
@RequiredArgsConstructor
public class OaFlowController {

    private final OaFlowMapper flowMapper;

    private static final int ST_ENABLED = 1;
    private static final int ST_DISABLED = 0;

    @Operation(summary = "分页查询流程")
    @GetMapping("/page")
    public Result<PageResult<OaFlow>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String flowName,
                                         @RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<OaFlow> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(flowName), OaFlow::getFlowName, flowName)
          .eq(StringUtils.hasText(bizType), OaFlow::getBizType, bizType)
          .eq(status != null, OaFlow::getStatus, status)
          .orderByDesc(OaFlow::getId);
        IPage<OaFlow> p = flowMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "流程详情")
    @GetMapping("/{id}")
    public Result<OaFlow> get(@PathVariable Long id) {
        return Result.ok(flowMapper.selectById(id));
    }

    @Operation(summary = "新增流程")
    @PostMapping
    public Result<Long> add(@RequestBody OaFlow flow) {
        if (flow.getStatus() == null) {
            flow.setStatus(ST_ENABLED);
        }
        flowMapper.insert(flow);
        return Result.ok(flow.getId());
    }

    @Operation(summary = "修改流程")
    @PutMapping
    public Result<Void> update(@RequestBody OaFlow flow) {
        flowMapper.updateById(flow);
        return Result.ok();
    }

    @Operation(summary = "删除流程")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        flowMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "启停切换")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        OaFlow flow = flowMapper.selectById(id);
        if (flow == null) {
            throw new BizException("流程不存在");
        }
        int target = flow.getStatus() != null && flow.getStatus() == ST_ENABLED ? ST_DISABLED : ST_ENABLED;
        flowMapper.update(null, new LambdaUpdateWrapper<OaFlow>()
                .eq(OaFlow::getId, id)
                .eq(OaFlow::getStatus, flow.getStatus())
                .set(OaFlow::getStatus, target));
        return Result.ok();
    }
}
