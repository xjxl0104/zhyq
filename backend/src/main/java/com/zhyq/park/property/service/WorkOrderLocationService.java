package com.zhyq.park.property.service;

import com.zhyq.park.building.entity.Building;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.mapper.BuildingMapper;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.building.service.FloorPlanService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.property.model.FloorLocationSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkOrderLocationService {
    private final BuildingMapper buildings;
    private final FloorMapper floors;
    private final SysFileMapper files;

    public record Location(Long projectId, Long buildingId, Long floorId, String zone, Long planFileId,
                           BigDecimal x, BigDecimal y) {}

    public Location resolve(FloorLocationSelection input, Long projectId, Long tenantId) {
        if (input == null) throw new BizException("缺少楼层定位信息");
        boolean hasPoint = input.x() != null || input.y() != null || input.planFileId() != null;
        if (input.buildingId() == null) {
            if (input.floorId() != null || input.zone() != null || hasPoint) throw new BizException("请先选择楼宇");
            return new Location(projectId, null, null, null, null, null, null);
        }
        Building building = buildings.selectById(input.buildingId());
        if (building == null || !Objects.equals(tenantId, building.getTenantId())) throw new BizException("楼宇不存在或不可用");
        if (projectId != null && !projectId.equals(building.getProjectId())) throw new BizException("楼宇不属于当前园区");
        Floor floor = input.floorId() == null ? null : floors.selectById(input.floorId());
        if (input.floorId() != null && (floor == null || !input.buildingId().equals(floor.getBuildingId())
                || !Objects.equals(building.getProjectId(), floor.getProjectId())
                || !Objects.equals(tenantId, floor.getTenantId()))) throw new BizException("楼层不属于所选楼宇");
        if (input.zone() != null && (floor == null || !java.util.Set.of("A", "B", "C").contains(input.zone()))) {
            throw new BizException("请先选择楼层和有效的A/B/C分区");
        }
        if (hasPoint) {
            if (floor == null || input.planFileId() == null || !coordinate(input.x()) || !coordinate(input.y())) {
                throw new BizException("请选择楼层平面图并标记有效位置");
            }
            SysFile plan = files.selectById(input.planFileId());
            if (plan == null || !FloorPlanService.BIZ_TYPE.equals(plan.getBizType())
                    || !input.floorId().equals(plan.getBizId()) || !Objects.equals(tenantId, plan.getTenantId())) {
                throw new BizException("平面图不属于所选楼层，请重新选择");
            }
        }
        return new Location(building.getProjectId(), building.getId(), input.floorId(), input.zone(),
                input.planFileId(), input.x(), input.y());
    }

    private static boolean coordinate(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    public Building building(Long id) { return id == null ? null : buildings.selectById(id); }
    public Floor floor(Long id) { return id == null ? null : floors.selectById(id); }
}
