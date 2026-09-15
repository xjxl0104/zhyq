package com.zhyq.park.building.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Building;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.mapper.BuildingMapper;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.common.exception.BizException;
import org.springframework.transaction.annotation.Transactional;
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
    private final BuildingMapper buildingMapper;
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

    /**
     * 系统没有单独的楼层维护页:楼宇还没有楼层时,按楼宇「层数」自动生成 1层..N层,返回楼层列表。
     * 已有楼层则原样返回,不重复生成。
     */
    @Operation(summary = "确保楼宇有楼层(无则按层数生成)并返回")
    @PostMapping("/ensure")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<Floor>> ensure(@RequestParam Long buildingId) {
        Building building = buildingMapper.selectById(buildingId);
        if (building == null) {
            throw new BizException("楼宇不存在或已删除");
        }
        LambdaQueryWrapper<Floor> qw = new LambdaQueryWrapper<Floor>()
                .eq(Floor::getBuildingId, buildingId)
                .orderByAsc(Floor::getSort).orderByAsc(Floor::getFloorNo);
        if (floorMapper.selectCount(qw) == 0) {
            int count = building.getFloorCount() == null || building.getFloorCount() < 1 ? 1 : building.getFloorCount();
            for (int i = 1; i <= count; i++) {
                Floor f = new Floor();
                f.setBuildingId(buildingId);
                f.setProjectId(building.getProjectId());
                f.setName(i + "层");
                f.setFloorNo(i);
                f.setSort(i);
                floorMapper.insert(f);
                try { spaceSyncService.sync("floor", f.getId()); } catch (Exception e) { log.warn("space sync fail floor {}", f.getId(), e); }
            }
        }
        return Result.ok(floorMapper.selectList(qw));
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
