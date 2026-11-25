package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.oa.entity.PeriodicTask;
import com.zhyq.park.oa.entity.PeriodicTaskLog;
import com.zhyq.park.oa.mapper.PeriodicTaskLogMapper;
import com.zhyq.park.oa.mapper.PeriodicTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Tag(name = "办公管理-周期任务年月表")
@RestController
@RequestMapping("/oa/periodic-task")
@RequiredArgsConstructor
public class PeriodicTaskController {

    private final PeriodicTaskMapper taskMapper;
    private final PeriodicTaskLogMapper logMapper;
    private final SysFileMapper sysFileMapper;

    @Operation(summary = "获取年度周期任务可视化矩阵")
    @GetMapping("/board")
    public Result<Map<String, Object>> board(@RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) String category) {
        int targetYear = (year != null && year >= 2020 && year <= 2099) ? year : LocalDate.now().getYear();
        LambdaQueryWrapper<PeriodicTask> tq = new LambdaQueryWrapper<>();
        tq.eq(StringUtils.hasText(category), PeriodicTask::getCategory, category)
          .orderByAsc(PeriodicTask::getSort)
          .orderByAsc(PeriodicTask::getId);
        List<PeriodicTask> tasks = taskMapper.selectList(tq);

        List<PeriodicTaskLog> logs = logMapper.selectList(new LambdaQueryWrapper<PeriodicTaskLog>()
                .eq(PeriodicTaskLog::getYearNo, targetYear)
                .orderByAsc(PeriodicTaskLog::getTaskId)
                .orderByAsc(PeriodicTaskLog::getMonthNo));

        if (!logs.isEmpty()) {
            List<Long> logIds = logs.stream().map(PeriodicTaskLog::getId).toList();
            List<SysFile> files = sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                    .eq(SysFile::getBizType, "oa_task_node")
                    .in(SysFile::getBizId, logIds));
            Map<Long, Integer> countMap = new HashMap<>();
            for (SysFile f : files) {
                countMap.merge(f.getBizId(), 1, Integer::sum);
            }
            for (PeriodicTaskLog log : logs) {
                log.setAttachmentCount(countMap.getOrDefault(log.getId(), 0));
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("year", targetYear);
        resp.put("tasks", tasks);
        resp.put("logs", logs);
        return Result.ok(resp);
    }

    @Operation(summary = "新增周期任务")
    @PostMapping
    public Result<PeriodicTask> add(@RequestBody PeriodicTask task) {
        validateTask(task);
        if (task.getStatus() == null) task.setStatus(1);
        if (task.getSort() == null) task.setSort(100);
        if (task.getNodeDay() == null || task.getNodeDay() < 1 || task.getNodeDay() > 28) task.setNodeDay(15);
        if (!StringUtils.hasText(task.getColor())) task.setColor("#4F46E5");
        taskMapper.insert(task);
        return Result.ok(task);
    }

    @Operation(summary = "修改周期任务")
    @PutMapping
    public Result<PeriodicTask> update(@RequestBody PeriodicTask task) {
        if (task.getId() == null) {
            throw new BizException("任务ID不能为空");
        }
        validateTask(task);
        if (task.getNodeDay() == null || task.getNodeDay() < 1 || task.getNodeDay() > 28) task.setNodeDay(15);
        taskMapper.updateById(task);
        return Result.ok(taskMapper.selectById(task.getId()));
    }

    @Operation(summary = "删除周期任务")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        logMapper.delete(new LambdaQueryWrapper<PeriodicTaskLog>().eq(PeriodicTaskLog::getTaskId, id));
        return Result.ok();
    }

    @Operation(summary = "更新某年某月任务节点状态与记录")
    @PutMapping("/log")
    public Result<PeriodicTaskLog> upsertLog(@RequestBody PeriodicTaskLog req) {
        if (req.getTaskId() == null || req.getYearNo() == null || req.getMonthNo() == null) {
            throw new BizException("任务ID、年份和月份不能为空");
        }
        if (req.getMonthNo() < 1 || req.getMonthNo() > 12) {
            throw new BizException("月份必须在 1~12 之间");
        }
        PeriodicTask task = taskMapper.selectById(req.getTaskId());
        if (task == null) {
            throw new BizException("周期任务不存在");
        }
        PeriodicTaskLog existing = logMapper.selectOne(new LambdaQueryWrapper<PeriodicTaskLog>()
                .eq(PeriodicTaskLog::getTaskId, req.getTaskId())
                .eq(PeriodicTaskLog::getYearNo, req.getYearNo())
                .eq(PeriodicTaskLog::getMonthNo, req.getMonthNo())
                .last("LIMIT 1"));
        PeriodicTaskLog target = existing != null ? existing : new PeriodicTaskLog();
        target.setTaskId(req.getTaskId());
        target.setYearNo(req.getYearNo());
        target.setMonthNo(req.getMonthNo());
        target.setStatus(req.getStatus() != null ? req.getStatus() : 0);
        target.setPlannedDate(req.getPlannedDate() != null ? req.getPlannedDate()
                : LocalDate.of(req.getYearNo(), req.getMonthNo(),
                Math.min(task.getNodeDay() != null ? task.getNodeDay() : 15, 28)));
        if (Integer.valueOf(2).equals(target.getStatus()) && req.getCompletedDate() == null) {
            target.setCompletedDate(LocalDate.now());
        } else {
            target.setCompletedDate(req.getCompletedDate());
        }
        target.setExecutor(req.getExecutor());
        target.setRemark(req.getRemark());

        if (existing == null) {
            logMapper.insert(target);
        } else {
            logMapper.updateById(target);
        }
        Long attachCount = sysFileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBizType, "oa_task_node")
                .eq(SysFile::getBizId, target.getId()));
        target.setAttachmentCount(attachCount != null ? attachCount.intValue() : 0);
        return Result.ok(target);
    }

    private void validateTask(PeriodicTask task) {
        if (!StringUtils.hasText(task.getName())) {
            throw new BizException("任务名称不能为空");
        }
        if (!StringUtils.hasText(task.getCycleType())) {
            task.setCycleType("MONTHLY");
        }
        if (!StringUtils.hasText(task.getMonthsJson())) {
            task.setMonthsJson(switch (task.getCycleType()) {
                case "QUARTERLY" -> "[3,6,9,12]";
                case "HALF_YEAR" -> "[6,12]";
                case "YEARLY" -> "[12]";
                default -> "[1,2,3,4,5,6,7,8,9,10,11,12]";
            });
        }
        if (!StringUtils.hasText(task.getCategory())) {
            task.setCategory("设施维保");
        }
    }
}
