package com.zhyq.park.building.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.space.service.SpaceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "建筑管理-楼层")
@RestController
@RequestMapping("/building/floor")
@RequiredArgsConstructor
public class FloorController {

    private final FloorMapper floorMapper;
    private final SpaceSyncService spaceSyncService;

    @Operation(summary = "楼层详情")
    @GetMapping("/{id}")
    public Result<Floor> get(@PathVariable Long id) {
        return Result.ok(floorMapper.selectById(id));
    }

    @Operation(summary = "新增楼层")
    @PostMapping
    public Result<Long> add(@RequestBody Floor floor) {
        floorMapper.insert(floor);
        try { spaceSyncService.sync("floor", floor.getId()); } catch (Exception e) { log.warn("space sync fail floor {}", floor.getId(), e); }
        return Result.ok(floor.getId());
    }

    @Operation(summary = "修改楼层")
    @PutMapping
    public Result<Void> update(@RequestBody Floor floor) {
        floorMapper.updateById(floor);
        try { spaceSyncService.sync("floor", floor.getId()); } catch (Exception e) { log.warn("space sync fail floor {}", floor.getId(), e); }
        return Result.ok();
    }

    @Operation(summary = "删除楼层")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        floorMapper.deleteById(id);
        try { spaceSyncService.remove("floor", id); } catch (Exception e) { log.warn("space remove fail floor {}", id, e); }
        return Result.ok();
    }

    @Operation(summary = "按楼宇查询楼层")
    @GetMapping("/list")
    public Result<List<Floor>> list(@RequestParam(required = false) Long buildingId) {
        LambdaQueryWrapper<Floor> qw = new LambdaQueryWrapper<>();
        qw.eq(buildingId != null, Floor::getBuildingId, buildingId)
          .orderByAsc(Floor::getSort).orderByAsc(Floor::getFloorNo);
        return Result.ok(floorMapper.selectList(qw));
    }
}
