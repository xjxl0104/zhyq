package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysPost;
import com.zhyq.park.system.mapper.SysPostMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-岗位")
@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostMapper postMapper;

    @GetMapping("/page")
    public Result<PageResult<SysPost>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<SysPost> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), SysPost::getName, name).orderByAsc(SysPost::getSort);
        IPage<SysPost> p = postMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<List<SysPost>> list() {
        return Result.ok(postMapper.selectList(new LambdaQueryWrapper<SysPost>().eq(SysPost::getStatus, 1)));
    }

    @PostMapping
    public Result<Long> add(@RequestBody SysPost post) {
        postMapper.insert(post);
        return Result.ok(post.getId());
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysPost post) {
        postMapper.updateById(post);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postMapper.deleteById(id);
        return Result.ok();
    }
}
