package com.zhyq.park.building.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.space.service.SpaceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "建筑管理-项目")
@RestController
@RequestMapping("/building/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;
    private final SpaceSyncService spaceSyncService;

    @Operation(summary = "分页查询项目")
    @GetMapping("/page")
    public Result<PageResult<Project>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Project::getName, name)
          .like(StringUtils.hasText(code), Project::getCode, code)
          .eq(status != null, Project::getStatus, status)
          .orderByDesc(Project::getId);
        IPage<Project> p = projectMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "项目详情")
    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.ok(projectMapper.selectById(id));
    }

    @Operation(summary = "新增项目")
    @PostMapping
    public Result<Long> add(@RequestBody Project project) {
        projectMapper.insert(project);
        try { spaceSyncService.sync("project", project.getId()); } catch (Exception e) { log.warn("space sync fail project {}", project.getId(), e); }
        return Result.ok(project.getId());
    }

    @Operation(summary = "修改项目")
    @PutMapping
    public Result<Void> update(@RequestBody Project project) {
        projectMapper.updateById(project);
        try { spaceSyncService.sync("project", project.getId()); } catch (Exception e) { log.warn("space sync fail project {}", project.getId(), e); }
        return Result.ok();
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectMapper.deleteById(id);
        try { spaceSyncService.remove("project", id); } catch (Exception e) { log.warn("space remove fail project {}", id, e); }
        return Result.ok();
    }

    @Operation(summary = "全部项目(下拉)")
    @GetMapping("/list")
    public Result<List<Project>> list() {
        return Result.ok(projectMapper.selectList(
                new LambdaQueryWrapper<Project>().orderByAsc(Project::getId)));
    }
}
