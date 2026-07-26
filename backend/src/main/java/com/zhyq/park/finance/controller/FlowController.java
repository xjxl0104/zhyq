package com.zhyq.park.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.finance.entity.Flow;
import com.zhyq.park.finance.mapper.FlowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "财务-收支流水")
@RestController
@RequestMapping("/finance/flow")
@RequiredArgsConstructor
public class FlowController {

    private final FlowMapper flowMapper;

    @Operation(summary = "分页查询流水")
    @PreAuthorize("hasAuthority('finance:flow:query')")
    @GetMapping("/page")
    public Result<PageResult<Flow>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Integer direction,
                                         @RequestParam(required = false) Long billId) {
        LambdaQueryWrapper<Flow> qw = new LambdaQueryWrapper<>();
        qw.eq(direction != null, Flow::getDirection, direction)
          .eq(billId != null, Flow::getBillId, billId)
          .orderByDesc(Flow::getId);
        IPage<Flow> p = flowMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
