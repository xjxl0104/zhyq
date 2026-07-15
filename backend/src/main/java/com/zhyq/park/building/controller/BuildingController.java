package com.zhyq.park.building.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.building.entity.Building;
import com.zhyq.park.building.mapper.BuildingMapper;
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
@Tag(name = "建筑管理-楼宇")
@RestController
@RequestMapping("/building/building")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingMapper buildingMapper;
    private final SpaceSyncService spaceSyncService;

    @Operation(summary = "分页查询楼宇")
    @GetMapping("/page")
    public Result<PageResult<Building>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String name,
                                             @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Building> qw = new LambdaQueryWrapper<>();
        qw.eq(projectId != null, Building::getProjectId, projectId)
          .like(StringUtils.hasText(name), Building::getName, name)
          .eq(status != null, Building::getStatus, status)
          .orderByAsc(Building::getSort).orderByDesc(Building::getId);
        IPage<Building> p = buildingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "楼宇详情")
    @GetMapping("/{id}")
    public Result<Building> get(@PathVariable Long id) {
        return Result.ok(buildingMapper.selectById(id));
    }

    @Operation(summary = "新增楼宇")
    @PostMapping
    public Result<Long> add(@RequestBody Building building) {
        buildingMapper.insert(building);
        try { spaceSyncService.sync("building", building.getId()); } catch (Exception e) { log.warn("space sync fail building {}", building.getId(), e); }
        return Result.ok(building.getId());
    }

    @Operation(summary = "修改楼宇")
    @PutMapping
    public Result<Void> update(@RequestBody Building building) {
        buildingMapper.updateById(building);
        try { spaceSyncService.sync("building", building.getId()); } catch (Exception e) { log.warn("space sync fail building {}", building.getId(), e); }
        return Result.ok();
    }

    @Operation(summary = "删除楼宇")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        buildingMapper.deleteById(id);
        try { spaceSyncService.remove("building", id); } catch (Exception e) { log.warn("space remove fail building {}", id, e); }
        return Result.ok();
    }

    @Operation(summary = "按项目查询楼宇(下拉/树)")
    @GetMapping("/list")
    public Result<List<Building>> list(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Building> qw = new LambdaQueryWrapper<>();
        qw.eq(projectId != null, Building::getProjectId, projectId)
          .orderByAsc(Building::getSort).orderByAsc(Building::getId);
        return Result.ok(buildingMapper.selectList(qw));
    }
}
