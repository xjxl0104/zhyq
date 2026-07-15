package com.zhyq.park.building.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.building.entity.Room;
import com.zhyq.park.building.mapper.RoomMapper;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.space.service.SpaceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "建筑管理-房源")
@RestController
@RequestMapping("/building/room")
@RequiredArgsConstructor
public class RoomController {

    private static final int STATUS_VACANT = 1;   // 可租
    private static final int STATUS_RENTED = 5;   // 在租

    private final RoomMapper roomMapper;
    private final SpaceSyncService spaceSyncService;

    @Operation(summary = "分页查询房源")
    @GetMapping("/page")
    public Result<PageResult<Room>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) Long buildingId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String roomNo) {
        LambdaQueryWrapper<Room> qw = new LambdaQueryWrapper<>();
        qw.eq(projectId != null, Room::getProjectId, projectId)
          .eq(buildingId != null, Room::getBuildingId, buildingId)
          .eq(status != null, Room::getStatus, status)
          .like(StringUtils.hasText(roomNo), Room::getRoomNo, roomNo)
          .orderByDesc(Room::getId);
        IPage<Room> p = roomMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "房源详情")
    @GetMapping("/{id}")
    public Result<Room> get(@PathVariable Long id) {
        return Result.ok(roomMapper.selectById(id));
    }

    @Operation(summary = "新增房源")
    @PostMapping
    public Result<Long> add(@RequestBody Room room) {
        roomMapper.insert(room);
        try { spaceSyncService.sync("room", room.getId()); } catch (Exception e) { log.warn("space sync fail room {}", room.getId(), e); }
        return Result.ok(room.getId());
    }

    @Operation(summary = "修改房源")
    @PutMapping
    public Result<Void> update(@RequestBody Room room) {
        roomMapper.updateById(room);
        try { spaceSyncService.sync("room", room.getId()); } catch (Exception e) { log.warn("space sync fail room {}", room.getId(), e); }
        return Result.ok();
    }

    @Operation(summary = "删除房源")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roomMapper.deleteById(id);
        try { spaceSyncService.remove("room", id); } catch (Exception e) { log.warn("space remove fail room {}", id, e); }
        return Result.ok();
    }

    @Operation(summary = "按楼层查询房源")
    @GetMapping("/list")
    public Result<List<Room>> list(@RequestParam(required = false) Long floorId) {
        LambdaQueryWrapper<Room> qw = new LambdaQueryWrapper<>();
        qw.eq(floorId != null, Room::getFloorId, floorId)
          .orderByAsc(Room::getRoomNo);
        return Result.ok(roomMapper.selectList(qw));
    }

    @Operation(summary = "房源统计(租控看板)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Room> qw = new LambdaQueryWrapper<>();
        qw.eq(projectId != null, Room::getProjectId, projectId);
        List<Room> rooms = roomMapper.selectList(qw);

        int totalRoom = rooms.size();
        int rentedCount = 0;
        BigDecimal manageArea = BigDecimal.ZERO;   // 管理面积 = sum(build_area)
        BigDecimal rentArea = BigDecimal.ZERO;     // 在租面积 (status=5)
        BigDecimal vacantArea = BigDecimal.ZERO;   // 待租面积 (status=1)
        BigDecimal priceSum = BigDecimal.ZERO;     // 在租房源租金底价合计

        for (Room r : rooms) {
            BigDecimal area = r.getBuildArea() == null ? BigDecimal.ZERO : r.getBuildArea();
            manageArea = manageArea.add(area);
            Integer st = r.getStatus();
            if (st != null && st == STATUS_RENTED) {
                rentedCount++;
                rentArea = rentArea.add(area);
                priceSum = priceSum.add(r.getBasePrice() == null ? BigDecimal.ZERO : r.getBasePrice());
            } else if (st != null && st == STATUS_VACANT) {
                vacantArea = vacantArea.add(area);
            }
        }

        // 出租率 = 在租面积 / 可租总面积(在租 + 待租),保留 1 位小数(百分数)
        BigDecimal rentableArea = rentArea.add(vacantArea);
        BigDecimal rentRate = BigDecimal.ZERO;
        if (rentableArea.compareTo(BigDecimal.ZERO) > 0) {
            rentRate = rentArea.multiply(BigDecimal.valueOf(100))
                    .divide(rentableArea, 1, RoundingMode.HALF_UP);
        }
        // 在租均价 = 在租房源租金底价平均,保留 2 位小数
        BigDecimal avgPrice = BigDecimal.ZERO;
        if (rentedCount > 0) {
            avgPrice = priceSum.divide(BigDecimal.valueOf(rentedCount), 2, RoundingMode.HALF_UP);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("rentedCount", rentedCount);
        stats.put("totalRoom", totalRoom);
        stats.put("manageArea", manageArea.setScale(2, RoundingMode.HALF_UP));
        stats.put("rentArea", rentArea.setScale(2, RoundingMode.HALF_UP));
        stats.put("vacantArea", vacantArea.setScale(2, RoundingMode.HALF_UP));
        stats.put("rentRate", rentRate);
        stats.put("avgPrice", avgPrice);
        return Result.ok(stats);
    }
}
