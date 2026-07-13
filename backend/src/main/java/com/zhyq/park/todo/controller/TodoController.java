package com.zhyq.park.todo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.todo.entity.Todo;
import com.zhyq.park.todo.mapper.TodoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "统一待办")
@RestController
@RequestMapping("/todo")
@RequiredArgsConstructor
public class TodoController {

    private final TodoMapper todoMapper;

    private static final int ST_TODO = 1; // 待办
    private static final int ST_READ = 2; // 已读
    private static final int ST_DONE = 3; // 已完成

    @Operation(summary = "分页查询待办")
    @GetMapping("/page")
    public Result<PageResult<Todo>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String owner) {
        LambdaQueryWrapper<Todo> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(bizType), Todo::getBizType, bizType)
          .eq(status != null, Todo::getStatus, status)
          .eq(StringUtils.hasText(owner), Todo::getOwner, owner)
          .orderByDesc(Todo::getId);
        IPage<Todo> p = todoMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "待办列表(status=1)")
    @GetMapping("/list")
    public Result<List<Todo>> list() {
        return Result.ok(todoMapper.selectList(
                new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getStatus, ST_TODO)
                        .orderByDesc(Todo::getId)));
    }

    @Operation(summary = "新增待办")
    @PostMapping
    public Result<Long> add(@RequestBody Todo todo) {
        if (todo.getStatus() == null) {
            todo.setStatus(ST_TODO);
        }
        todoMapper.insert(todo);
        return Result.ok(todo.getId());
    }

    @Operation(summary = "修改待办")
    @PutMapping
    public Result<Void> update(@RequestBody Todo todo) {
        todoMapper.updateById(todo);
        return Result.ok();
    }

    @Operation(summary = "标记完成")
    @PostMapping("/{id}/done")
    public Result<Void> done(@PathVariable Long id) {
        Todo upd = new Todo();
        upd.setId(id);
        upd.setStatus(ST_DONE);
        todoMapper.updateById(upd);
        return Result.ok();
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        Todo upd = new Todo();
        upd.setId(id);
        upd.setStatus(ST_READ);
        todoMapper.updateById(upd);
        return Result.ok();
    }

    @Operation(summary = "删除待办")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        todoMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "待办统计(各 bizType 待办数)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        String[] types = {"contract", "bill", "workorder", "lead", "approval"};
        long total = 0;
        for (String t : types) {
            long c = todoMapper.selectCount(
                    new LambdaQueryWrapper<Todo>()
                            .eq(Todo::getBizType, t)
                            .eq(Todo::getStatus, ST_TODO));
            m.put(t, c);
            total += c;
        }
        m.put("total", total);
        return Result.ok(m);
    }
}
