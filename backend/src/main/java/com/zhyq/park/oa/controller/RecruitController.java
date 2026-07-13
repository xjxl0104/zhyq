package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Recruit;
import com.zhyq.park.oa.mapper.RecruitMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "办公管理-人才招聘")
@RestController
@RequestMapping("/oa/recruit")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitMapper recruitMapper;

    // 招聘状态:1招聘中 2已关闭
    private static final int ST_OPEN = 1;
    private static final int ST_CLOSED = 2;

    @Operation(summary = "分页查询招聘")
    @GetMapping("/page")
    public Result<PageResult<Recruit>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String postName,
                                            @RequestParam(required = false) String dept,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Recruit> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(postName), Recruit::getPostName, postName)
          .eq(StringUtils.hasText(dept), Recruit::getDept, dept)
          .eq(status != null, Recruit::getStatus, status)
          .orderByDesc(Recruit::getId);
        IPage<Recruit> p = recruitMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "招聘详情")
    @GetMapping("/{id}")
    public Result<Recruit> get(@PathVariable Long id) {
        return Result.ok(recruitMapper.selectById(id));
    }

    @Operation(summary = "新增招聘")
    @PostMapping
    public Result<Long> add(@RequestBody Recruit recruit) {
        recruitMapper.insert(recruit);
        return Result.ok(recruit.getId());
    }

    @Operation(summary = "修改招聘")
    @PutMapping
    public Result<Void> update(@RequestBody Recruit recruit) {
        recruitMapper.updateById(recruit);
        return Result.ok();
    }

    @Operation(summary = "删除招聘")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recruitMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "关闭职位:招聘中(1)→已关闭(2)")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        int updated = recruitMapper.update(null, new LambdaUpdateWrapper<Recruit>()
                .eq(Recruit::getId, id)
                .eq(Recruit::getStatus, ST_OPEN)
                .set(Recruit::getStatus, ST_CLOSED));
        if (updated == 0) {
            throw new BizException("仅招聘中的职位可关闭");
        }
        return Result.ok();
    }

    @Operation(summary = "模拟投递:投递数原子+1,仅招聘中可投递")
    @PostMapping("/{id}/apply")
    public Result<Void> apply(@PathVariable Long id) {
        LambdaUpdateWrapper<Recruit> uw = new LambdaUpdateWrapper<>();
        uw.eq(Recruit::getId, id)
          .eq(Recruit::getStatus, ST_OPEN)
          .setSql("applicants = applicants + 1");
        int updated = recruitMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅招聘中的职位可投递");
        }
        return Result.ok();
    }
}
