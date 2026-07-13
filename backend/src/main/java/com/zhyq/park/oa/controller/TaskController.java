package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Task;
import com.zhyq.park.oa.mapper.TaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "办公管理-任务")
@RestController
@RequestMapping("/oa/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskMapper taskMapper;

    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public Result<PageResult<Task>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String owner) {
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Task::getTitle, title)
          .eq(status != null, Task::getStatus, status)
          .eq(StringUtils.hasText(owner), Task::getOwner, owner)
          .orderByDesc(Task::getId);
        IPage<Task> p = taskMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<Task> get(@PathVariable Long id) {
        return Result.ok(taskMapper.selectById(id));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    public Result<Long> add(@RequestBody Task task) {
        taskMapper.insert(task);
        return Result.ok(task.getId());
    }

    @Operation(summary = "修改任务")
    @PutMapping
    public Result<Void> update(@RequestBody Task task) {
        taskMapper.updateById(task);
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "完成任务(状态→3已完成)")
    @PostMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id) {
        Task task = taskMapper.selectById(id);
        if (task != null) {
            task.setStatus(3);
            taskMapper.updateById(task);
        }
        return Result.ok();
    }
}
