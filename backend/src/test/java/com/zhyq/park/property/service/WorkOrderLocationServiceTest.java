package com.zhyq.park.property.service;

import com.zhyq.park.building.entity.Building;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.mapper.BuildingMapper;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.property.model.FloorLocationSelection;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkOrderLocationServiceTest {
    final BuildingMapper buildings = mock(BuildingMapper.class);
    final FloorMapper floors = mock(FloorMapper.class);
    final SysFileMapper files = mock(SysFileMapper.class);
    final WorkOrderLocationService service = new WorkOrderLocationService(buildings, floors, files);
    WorkOrderLocationServiceTest() {
        Building b = new Building(); b.setId(2L); b.setProjectId(3L); b.setTenantId(1L);
        when(buildings.selectById(2L)).thenReturn(b);
        Floor f = new Floor(); f.setId(4L); f.setBuildingId(2L); f.setProjectId(3L); f.setTenantId(1L);
        when(floors.selectById(4L)).thenReturn(f);
        SysFile plan = new SysFile(); plan.setId(5L); plan.setBizType("floor_plan"); plan.setBizId(4L); plan.setTenantId(1L);
        when(files.selectById(5L)).thenReturn(plan);
    }
    FloorLocationSelection selection(String x, String y) {
        return new FloorLocationSelection(2L, 4L, 5L, x == null ? null : new BigDecimal(x), y == null ? null : new BigDecimal(y));
    }
    @Test void retainsExactFileVersionAndCoordinatesIncludingImageEdges() {
        var location = service.resolve(selection("0", "1"), 3L, 1L);
        assertThat(location.planFileId()).isEqualTo(5L);
        assertThat(location.x()).isEqualByComparingTo("0");
        assertThat(location.y()).isEqualByComparingTo("1");
        assertThat(location.projectId()).isEqualTo(3L);
    }
    @Test void rejectsIncompleteAndOutOfBoundsCoordinates() {
        assertThatThrownBy(() -> service.resolve(selection(null, "0.5"), 3L, 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.resolve(selection("-0.1", "0.5"), 3L, 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.resolve(selection("1.1", "0.5"), 3L, 1L)).isInstanceOf(BizException.class);
    }
    @Test void rejectsAnotherFloorPlanAndAnotherProjectOrTenant() {
        SysFile other = new SysFile(); other.setBizType("floor_plan"); other.setBizId(99L); other.setTenantId(1L);
        when(files.selectById(5L)).thenReturn(other);
        assertThatThrownBy(() -> service.resolve(selection("0.5", "0.5"), 3L, 1L)).hasMessageContaining("不属于所选楼层");
        assertThatThrownBy(() -> service.resolve(selection("0.5", "0.5"), 99L, 1L)).hasMessageContaining("不属于当前园区");
        assertThatThrownBy(() -> service.resolve(selection("0.5", "0.5"), 3L, 9L)).hasMessageContaining("不可用");
    }
    @Test void supportsFloorWithoutPlanAndExplicitClear() {
        var noPlan = service.resolve(new FloorLocationSelection(2L, 4L, null, null, null), 3L, 1L);
        assertThat(noPlan.floorId()).isEqualTo(4L);
        assertThat(noPlan.planFileId()).isNull();
        var clear = service.resolve(new FloorLocationSelection(null, null, null, null, null), 3L, 1L);
        assertThat(clear.buildingId()).isNull();
        assertThat(clear.projectId()).isEqualTo(3L);
    }
}
