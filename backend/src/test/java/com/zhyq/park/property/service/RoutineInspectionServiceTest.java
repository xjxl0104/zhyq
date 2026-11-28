package com.zhyq.park.property.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.property.entity.PmRoutineTask;
import com.zhyq.park.property.entity.PmRoutineTaskItem;
import com.zhyq.park.property.entity.PmRoutineTemplate;
import com.zhyq.park.property.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutineInspectionServiceTest {

    @Mock PmInspectorMapper inspectorMapper;
    @Mock PmRoutineTemplateMapper templateMapper;
    @Mock PmRoutineTemplateItemMapper templateItemMapper;
    @Mock PmRoutineTaskMapper taskMapper;
    @Mock PmRoutineTaskItemMapper taskItemMapper;
    @Mock WorkOrderMapper workOrderMapper;

    private RoutineInspectionService service() {
        return new RoutineInspectionService(
                inspectorMapper, templateMapper, templateItemMapper,
                taskMapper, taskItemMapper, workOrderMapper, new ObjectMapper()
        );
    }

    @Test
    void computeDaySlotsSupports24hIntervalAndFixedSlotsAndWeekdays() {
        PmRoutineTemplate intervalTpl = new PmRoutineTemplate();
        intervalTpl.setFreqType("INTERVAL_HOURS");
        intervalTpl.setIntervalHours(4);
        intervalTpl.setStartTime("00:00");
        intervalTpl.setEndTime("24:00");
        intervalTpl.setWeekdays("1,2,3,4,5,6,7");

        List<RoutineInspectionService.SlotDef> six = RoutineInspectionService.computeDaySlots(intervalTpl, LocalDate.of(2026, 11, 28));
        assertEquals(6, six.size());
        assertEquals("00:00-04:00", six.get(0).label());
        assertEquals("20:00-24:00", six.get(5).label());

        PmRoutineTemplate slotTpl = new PmRoutineTemplate();
        slotTpl.setFreqType("DAILY_SLOTS");
        slotTpl.setTimeSlots("09:00,15:00,21:00");
        slotTpl.setWeekdays("1,2,3,4,5"); // 周一至周五
        // 2026-11-28 是周六，不应生成任务
        assertTrue(RoutineInspectionService.computeDaySlots(slotTpl, LocalDate.of(2026, 11, 28)).isEmpty());
        // 2026-11-27 是周五，应生成 3 个班次
        List<RoutineInspectionService.SlotDef> three = RoutineInspectionService.computeDaySlots(slotTpl, LocalDate.of(2026, 11, 27));
        assertEquals(3, three.size());
        assertEquals("09:00 班次", three.get(0).label());
        assertEquals("21:00 班次", three.get(2).label());
    }

    @Test
    void submitItemCaptureRejectsWithoutPhotoAndAutoCompletesOnlyWhenAllItemsDone() {
        RoutineInspectionService svc = service();

        PmRoutineTask task = new PmRoutineTask();
        task.setId(10L);
        task.setTotalItems(2);
        task.setCompletedItems(0);
        task.setStatus(PmRoutineTask.ST_PENDING);
        task.setDeadlineTime(LocalDateTime.now().plusHours(4));
        task.setInspectorName("张工");

        PmRoutineTaskItem i1 = new PmRoutineTaskItem();
        i1.setId(101L); i1.setTaskId(10L); i1.setMinPhotos(1); i1.setChecked(0); i1.setItemName("配电柜");
        PmRoutineTaskItem i2 = new PmRoutineTaskItem();
        i2.setId(102L); i2.setTaskId(10L); i2.setMinPhotos(1); i2.setChecked(0); i2.setItemName("水泵房");

        when(taskMapper.selectById(10L)).thenReturn(task);
        when(taskItemMapper.selectById(101L)).thenReturn(i1);
        when(taskItemMapper.selectById(102L)).thenReturn(i2);

        // 1) 未传照片必须拦截，禁止手动勾选完成
        assertThrows(BizException.class, () -> svc.submitItemCapture(10L, 101L,
                new RoutineInspectionService.CaptureItemReq(List.of(), "NORMAL", null, null, "张工")));

        // 2) 拍完第 1 项 -> 任务进入 IN_PROGRESS，不会提前完成
        when(taskItemMapper.selectList(any())).thenReturn(List.of(i1, i2));
        Map<String, Object> r1 = svc.submitItemCapture(10L, 101L,
                new RoutineInspectionService.CaptureItemReq(
                        List.of(new RoutineInspectionService.PhotoProof(501L, "/api/file/download/501", "2026-11-28 09:05:00", "wm")),
                        "NORMAL", null, null, "张工"));
        assertEquals(Boolean.FALSE, r1.get("autoCompleted"));
        assertEquals(PmRoutineTask.ST_IN_PROGRESS, task.getStatus());
        assertEquals(1, task.getCompletedItems());

        // 3) 拍完第 2 项（带异常） -> 全部必拍项完成，系统自动置为 COMPLETED 且累计 issueCount=1
        Map<String, Object> r2 = svc.submitItemCapture(10L, 102L,
                new RoutineInspectionService.CaptureItemReq(
                        List.of(new RoutineInspectionService.PhotoProof(502L, "/api/file/download/502", "2026-11-28 09:10:00", "wm")),
                        "ABNORMAL", "设备故障", "水压表指针偏低", "张工"));
        assertEquals(Boolean.TRUE, r2.get("autoCompleted"));
        assertEquals(PmRoutineTask.ST_COMPLETED, task.getStatus());
        assertEquals(2, task.getCompletedItems());
        assertEquals(1, task.getIssueCount());
        assertNotNull(task.getCompletedTime());
    }
}
