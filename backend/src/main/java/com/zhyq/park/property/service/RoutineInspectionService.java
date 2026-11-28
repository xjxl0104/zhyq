package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.property.entity.PmInspector;
import com.zhyq.park.property.entity.PmRoutineTask;
import com.zhyq.park.property.entity.PmRoutineTaskItem;
import com.zhyq.park.property.entity.PmRoutineTemplate;
import com.zhyq.park.property.entity.PmRoutineTemplateItem;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.PmInspectorMapper;
import com.zhyq.park.property.mapper.PmRoutineTaskItemMapper;
import com.zhyq.park.property.mapper.PmRoutineTaskMapper;
import com.zhyq.park.property.mapper.PmRoutineTemplateItemMapper;
import com.zhyq.park.property.mapper.PmRoutineTemplateMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物业日常巡检/检查统一引擎（设备巡检 / 安防巡更 / 保洁检查 / 绿化检查）：
 * 1. 巡检人花名册管理（姓名、电话、职位、负责条线）；
 * 2. 巡检模板与固定检查项管理（可自由修改、设置24小时周期内频率）；
 * 3. 按模板频率自动生成当日/每班次巡检任务；
 * 4. 严禁手动标记完成：仅当所有预设检查项全部完成「即时拍照+水印」打卡后，系统自动将任务置为 COMPLETED；
 * 5. 未完成/超时任务自动判定标红，发现异常支持一键转报修工单；
 * 6. 提供日/月/年汇总、巡检人绩效汇总、异常问题分类与高频区域分析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineInspectionService {

    public static final Set<String> MODULES = Set.of("EQUIPMENT", "PATROL", "CLEANING", "GREENING");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final PmInspectorMapper inspectorMapper;
    private final PmRoutineTemplateMapper templateMapper;
    private final PmRoutineTemplateItemMapper templateItemMapper;
    private final PmRoutineTaskMapper taskMapper;
    private final PmRoutineTaskItemMapper taskItemMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ObjectMapper objectMapper;

    public record SlotDef(String label, LocalDateTime start, LocalDateTime deadline) {}

    public record TemplateSaveReq(
            Long id,
            String moduleType,
            String name,
            String area,
            Long inspectorId,
            String inspectorName,
            String inspectorPhone,
            String inspectorPosition,
            String freqType,
            Integer intervalHours,
            String startTime,
            String endTime,
            String timeSlots,
            String weekdays,
            Integer status,
            Integer sortOrder,
            String remark,
            List<TemplateItemReq> items
    ) {}

    public record TemplateItemReq(
            Long id,
            Integer seqNo,
            String itemName,
            String areaPoint,
            String standard,
            Integer requirePhoto,
            Integer minPhotos
    ) {}

    public record PhotoProof(Long fileId, String url, String capturedAt, String watermark) {}

    public record CaptureItemReq(
            List<PhotoProof> photos,
            String result,
            String issueCategory,
            String issueDesc,
            String capturedBy
    ) {}

    // ------------------------------------------------------------------ 巡检人管理

    public List<PmInspector> listInspectors(String moduleType, Boolean onlyActive) {
        LambdaQueryWrapper<PmInspector> qw = new LambdaQueryWrapper<PmInspector>()
                .eq(Boolean.TRUE.equals(onlyActive), PmInspector::getStatus, 1)
                .orderByAsc(PmInspector::getId);
        List<PmInspector> all = inspectorMapper.selectList(qw);
        if (!StringUtils.hasText(moduleType) || "ALL".equalsIgnoreCase(moduleType)) {
            return all;
        }
        String mod = normalizeModule(moduleType);
        return all.stream()
                .filter(i -> !StringUtils.hasText(i.getModuleScope())
                        || "ALL".equalsIgnoreCase(i.getModuleScope())
                        || Arrays.stream(i.getModuleScope().split(","))
                        .map(String::trim).anyMatch(mod::equalsIgnoreCase))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PmInspector saveInspector(PmInspector req) {
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException("请填写巡检人姓名");
        }
        if (req.getStatus() == null) req.setStatus(1);
        if (!StringUtils.hasText(req.getModuleScope())) req.setModuleScope("ALL");
        if (req.getId() == null) {
            inspectorMapper.insert(req);
        } else {
            PmInspector existed = inspectorMapper.selectById(req.getId());
            if (existed == null) throw new BizException("巡检人不存在");
            existed.setName(req.getName().trim());
            existed.setPhone(nvl(req.getPhone()));
            existed.setPosition(nvl(req.getPosition()));
            existed.setModuleScope(req.getModuleScope());
            existed.setStatus(req.getStatus());
            existed.setRemark(nvl(req.getRemark()));
            inspectorMapper.updateById(existed);
            // 同步更新关联启用模板中的巡检人快照信息
            List<PmRoutineTemplate> tpls = templateMapper.selectList(new LambdaQueryWrapper<PmRoutineTemplate>()
                    .eq(PmRoutineTemplate::getInspectorId, existed.getId()));
            for (PmRoutineTemplate t : tpls) {
                t.setInspectorName(existed.getName());
                t.setInspectorPhone(existed.getPhone());
                t.setInspectorPosition(existed.getPosition());
                templateMapper.updateById(t);
            }
            return existed;
        }
        return req;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteInspector(Long id) {
        inspectorMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 巡检模板与固定检查项

    public List<Map<String, Object>> listTemplates(String moduleType) {
        String mod = normalizeModule(moduleType);
        List<PmRoutineTemplate> tpls = templateMapper.selectList(new LambdaQueryWrapper<PmRoutineTemplate>()
                .eq(PmRoutineTemplate::getModuleType, mod)
                .orderByAsc(PmRoutineTemplate::getSortOrder)
                .orderByAsc(PmRoutineTemplate::getId));
        if (tpls.isEmpty()) return List.of();
        List<Long> ids = tpls.stream().map(PmRoutineTemplate::getId).toList();
        List<PmRoutineTemplateItem> allItems = templateItemMapper.selectList(new LambdaQueryWrapper<PmRoutineTemplateItem>()
                .in(PmRoutineTemplateItem::getTemplateId, ids)
                .orderByAsc(PmRoutineTemplateItem::getSeqNo)
                .orderByAsc(PmRoutineTemplateItem::getId));
        Map<Long, List<PmRoutineTemplateItem>> byTpl = allItems.stream()
                .collect(Collectors.groupingBy(PmRoutineTemplateItem::getTemplateId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PmRoutineTemplate t : tpls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("template", t);
            m.put("items", byTpl.getOrDefault(t.getId(), List.of()));
            out.add(m);
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveTemplate(TemplateSaveReq req) {
        if (req == null || !StringUtils.hasText(req.name())) {
            throw new BizException("请填写巡检模板名称");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException("请至少配置一条预设检查项");
        }
        String mod = normalizeModule(req.moduleType());
        PmRoutineTemplate tpl = req.id() != null ? templateMapper.selectById(req.id()) : new PmRoutineTemplate();
        if (tpl == null) throw new BizException("巡检模板不存在");

        tpl.setModuleType(mod);
        tpl.setName(req.name().trim());
        tpl.setArea(nvl(req.area()));

        // 绑定巡检人快照
        if (req.inspectorId() != null) {
            PmInspector ins = inspectorMapper.selectById(req.inspectorId());
            if (ins != null) {
                tpl.setInspectorId(ins.getId());
                tpl.setInspectorName(ins.getName());
                tpl.setInspectorPhone(nvl(ins.getPhone()));
                tpl.setInspectorPosition(nvl(ins.getPosition()));
            }
        } else {
            tpl.setInspectorId(null);
            tpl.setInspectorName(nvl(req.inspectorName()));
            tpl.setInspectorPhone(nvl(req.inspectorPhone()));
            tpl.setInspectorPosition(nvl(req.inspectorPosition()));
        }

        String freq = "INTERVAL_HOURS".equalsIgnoreCase(req.freqType()) ? "INTERVAL_HOURS" : "DAILY_SLOTS";
        tpl.setFreqType(freq);
        int interval = req.intervalHours() == null ? 24 : Math.max(1, Math.min(24, req.intervalHours()));
        tpl.setIntervalHours(interval);
        tpl.setStartTime(validHm(req.startTime(), "08:00"));
        tpl.setEndTime(validEndHm(req.endTime(), "20:00"));
        tpl.setTimeSlots(StringUtils.hasText(req.timeSlots()) ? req.timeSlots().trim() : "09:00");
        tpl.setWeekdays(StringUtils.hasText(req.weekdays()) ? req.weekdays().trim() : "1,2,3,4,5,6,7");
        tpl.setStatus(req.status() == null ? 1 : req.status());
        tpl.setSortOrder(req.sortOrder() == null ? 10 : req.sortOrder());
        tpl.setRemark(nvl(req.remark()));

        if (tpl.getId() == null) {
            templateMapper.insert(tpl);
        } else {
            templateMapper.updateById(tpl);
            templateItemMapper.delete(new LambdaQueryWrapper<PmRoutineTemplateItem>()
                    .eq(PmRoutineTemplateItem::getTemplateId, tpl.getId()));
        }

        List<PmRoutineTemplateItem> savedItems = new ArrayList<>();
        int seq = 1;
        for (TemplateItemReq it : req.items()) {
            if (it == null || !StringUtils.hasText(it.itemName())) continue;
            PmRoutineTemplateItem entity = new PmRoutineTemplateItem();
            entity.setTemplateId(tpl.getId());
            entity.setSeqNo(seq++);
            entity.setItemName(it.itemName().trim());
            entity.setAreaPoint(nvl(it.areaPoint()));
            entity.setStandard(nvl(it.standard()));
            entity.setRequirePhoto(1); // 严格要求现场即时拍照
            entity.setMinPhotos(it.minPhotos() == null || it.minPhotos() < 1 ? 1 : Math.min(5, it.minPhotos()));
            templateItemMapper.insert(entity);
            savedItems.add(entity);
        }
        if (savedItems.isEmpty()) {
            throw new BizException("请至少填写一条有效检查项内容");
        }

        // 若模板启用，立即幂等补齐今日尚未生成的巡检任务
        if (Integer.valueOf(1).equals(tpl.getStatus())) {
            ensureTasksForTemplateOnDate(tpl, savedItems, LocalDate.now(), LocalDateTime.now());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("template", tpl);
        out.put("items", savedItems);
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
        templateItemMapper.delete(new LambdaQueryWrapper<PmRoutineTemplateItem>()
                .eq(PmRoutineTemplateItem::getTemplateId, id));
    }

    // ------------------------------------------------------------------ 24小时任务生成与调度

    @Scheduled(cron = "0 2 0 * * ?")
    public void scheduledDailyGenerate() {
        LocalDate today = LocalDate.now();
        for (String mod : MODULES) {
            try {
                ensureTasksForDate(mod, today);
            } catch (Exception e) {
                log.warn("自动生成 {} 巡检任务失败: {}", mod, e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void scheduledRefreshOverdue() {
        refreshOverdueStatus(LocalDateTime.now());
    }

    /** 幂等补齐某模块某天的全部巡检班次任务，并刷新超时标红状态 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureTasksForDate(String moduleType, LocalDate date) {
        String mod = normalizeModule(moduleType);
        LocalDateTime now = LocalDateTime.now();
        List<PmRoutineTemplate> tpls = templateMapper.selectList(new LambdaQueryWrapper<PmRoutineTemplate>()
                .eq(PmRoutineTemplate::getModuleType, mod)
                .eq(PmRoutineTemplate::getStatus, 1)
                .orderByAsc(PmRoutineTemplate::getSortOrder)
                .orderByAsc(PmRoutineTemplate::getId));
        if (!tpls.isEmpty()) {
            List<Long> ids = tpls.stream().map(PmRoutineTemplate::getId).toList();
            Map<Long, List<PmRoutineTemplateItem>> itemMap = templateItemMapper.selectList(
                    new LambdaQueryWrapper<PmRoutineTemplateItem>()
                            .in(PmRoutineTemplateItem::getTemplateId, ids)
                            .orderByAsc(PmRoutineTemplateItem::getSeqNo)
                            .orderByAsc(PmRoutineTemplateItem::getId))
                    .stream().collect(Collectors.groupingBy(PmRoutineTemplateItem::getTemplateId));
            for (PmRoutineTemplate tpl : tpls) {
                List<PmRoutineTemplateItem> items = itemMap.getOrDefault(tpl.getId(), List.of());
                if (!items.isEmpty()) {
                    ensureTasksForTemplateOnDate(tpl, items, date, now);
                }
            }
        }
        refreshOverdueStatus(now);
    }

    private void ensureTasksForTemplateOnDate(PmRoutineTemplate tpl, List<PmRoutineTemplateItem> items,
                                              LocalDate date, LocalDateTime now) {
        List<SlotDef> slots = computeDaySlots(tpl, date);
        if (slots.isEmpty()) return;
        List<PmRoutineTask> existed = taskMapper.selectList(new LambdaQueryWrapper<PmRoutineTask>()
                .eq(PmRoutineTask::getTemplateId, tpl.getId())
                .eq(PmRoutineTask::getTaskDate, date));
        Set<String> existedLabels = existed.stream().map(PmRoutineTask::getSlotLabel).collect(Collectors.toSet());

        for (SlotDef slot : slots) {
            if (existedLabels.contains(slot.label())) continue;
            PmRoutineTask task = new PmRoutineTask();
            task.setModuleType(tpl.getModuleType());
            task.setTemplateId(tpl.getId());
            task.setTemplateName(tpl.getName());
            task.setArea(tpl.getArea());
            task.setTaskDate(date);
            task.setSlotLabel(slot.label());
            task.setScheduledStart(slot.start());
            task.setDeadlineTime(slot.deadline());
            task.setInspectorId(tpl.getInspectorId());
            task.setInspectorName(tpl.getInspectorName());
            task.setInspectorPhone(tpl.getInspectorPhone());
            task.setInspectorPosition(tpl.getInspectorPosition());
            task.setTotalItems(items.size());
            task.setCompletedItems(0);
            task.setIssueCount(0);
            task.setStatus(now.isAfter(slot.deadline()) ? PmRoutineTask.ST_OVERDUE : PmRoutineTask.ST_PENDING);
            taskMapper.insert(task);

            int seq = 1;
            for (PmRoutineTemplateItem ti : items) {
                PmRoutineTaskItem item = new PmRoutineTaskItem();
                item.setTaskId(task.getId());
                item.setTemplateItemId(ti.getId());
                item.setSeqNo(seq++);
                item.setItemName(ti.getItemName());
                item.setAreaPoint(StringUtils.hasText(ti.getAreaPoint()) ? ti.getAreaPoint() : tpl.getArea());
                item.setStandard(ti.getStandard());
                item.setRequirePhoto(1);
                item.setMinPhotos(ti.getMinPhotos() == null || ti.getMinPhotos() < 1 ? 1 : ti.getMinPhotos());
                item.setChecked(0);
                item.setResult("NORMAL");
                taskItemMapper.insert(item);
            }
        }
    }

    /** 按模板频率（24小时周期内固定时段或每隔N小时）计算指定日期的全部班次窗口 */
    public static List<SlotDef> computeDaySlots(PmRoutineTemplate tpl, LocalDate date) {
        if (tpl == null || date == null) return List.of();
        int dow = date.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
        if (StringUtils.hasText(tpl.getWeekdays())) {
            Set<String> allowed = Arrays.stream(tpl.getWeekdays().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            if (!allowed.isEmpty() && !allowed.contains(String.valueOf(dow))) {
                return List.of();
            }
        }
        LocalDateTime dayEnd = date.atTime(23, 59, 59);
        List<SlotDef> out = new ArrayList<>();

        if ("INTERVAL_HOURS".equalsIgnoreCase(tpl.getFreqType())) {
            int stepHours = tpl.getIntervalHours() == null ? 24 : Math.max(1, Math.min(24, tpl.getIntervalHours()));
            int startMin = parseMinutes(tpl.getStartTime(), 0);
            int endMin = parseEndMinutes(tpl.getEndTime(), 24 * 60);
            if (endMin <= startMin) endMin = 24 * 60;
            if (stepHours >= 24) {
                LocalDateTime st = date.atStartOfDay().plusMinutes(startMin);
                out.add(new SlotDef("全天(24h)", st, dayEnd));
                return out;
            }
            int stepMin = stepHours * 60;
            for (int cur = startMin; cur < endMin; cur += stepMin) {
                int next = Math.min(cur + stepMin, endMin);
                LocalDateTime st = date.atStartOfDay().plusMinutes(cur);
                LocalDateTime dl = next >= 24 * 60 ? dayEnd : date.atStartOfDay().plusMinutes(next);
                String label = String.format("%02d:%02d-%02d:%02d", cur / 60, cur % 60,
                        next >= 24 * 60 ? 24 : next / 60, next >= 24 * 60 ? 0 : next % 60);
                out.add(new SlotDef(label, st, dl));
            }
            return out;
        }

        // DAILY_SLOTS：每日固定时段
        String rawSlots = StringUtils.hasText(tpl.getTimeSlots()) ? tpl.getTimeSlots() : "09:00";
        List<Integer> mins = Arrays.stream(rawSlots.split("[,，;；\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> parseMinutes(s, -1))
                .filter(v -> v >= 0 && v < 24 * 60)
                .distinct().sorted().toList();
        if (mins.isEmpty()) {
            out.add(new SlotDef("全天(24h)", date.atTime(8, 0), dayEnd));
            return out;
        }
        for (int i = 0; i < mins.size(); i++) {
            int cur = mins.get(i);
            LocalDateTime st = date.atStartOfDay().plusMinutes(cur);
            LocalDateTime dl = (i + 1 < mins.size())
                    ? date.atStartOfDay().plusMinutes(mins.get(i + 1))
                    : dayEnd;
            String label = mins.size() == 1
                    ? String.format("%02d:%02d 每日班次", cur / 60, cur % 60)
                    : String.format("%02d:%02d 班次", cur / 60, cur % 60);
            out.add(new SlotDef(label, st, dl));
        }
        return out;
    }

    private void refreshOverdueStatus(LocalDateTime now) {
        List<PmRoutineTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<PmRoutineTask>()
                .in(PmRoutineTask::getStatus, PmRoutineTask.ST_PENDING, PmRoutineTask.ST_IN_PROGRESS)
                .lt(PmRoutineTask::getDeadlineTime, now));
        for (PmRoutineTask t : candidates) {
            t.setStatus(PmRoutineTask.ST_OVERDUE);
            taskMapper.updateById(t);
        }
    }

    // ------------------------------------------------------------------ 任务列表、详情与即时拍照打卡

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> listTasks(String moduleType, LocalDate date, String status, Long inspectorId) {
        String mod = normalizeModule(moduleType);
        LocalDate target = date != null ? date : LocalDate.now();
        // 今日（含今天）自动按模板补齐当日班次任务，避免漏单
        if (target.equals(LocalDate.now())) {
            ensureTasksForDate(mod, target);
        } else {
            refreshOverdueStatus(LocalDateTime.now());
        }

        LambdaQueryWrapper<PmRoutineTask> qw = new LambdaQueryWrapper<PmRoutineTask>()
                .eq(PmRoutineTask::getModuleType, mod)
                .eq(PmRoutineTask::getTaskDate, target)
                .eq(inspectorId != null, PmRoutineTask::getInspectorId, inspectorId)
                .orderByAsc(PmRoutineTask::getScheduledStart)
                .orderByAsc(PmRoutineTask::getId);
        if (StringUtils.hasText(status)) {
            if ("INCOMPLETE".equalsIgnoreCase(status)) {
                qw.ne(PmRoutineTask::getStatus, PmRoutineTask.ST_COMPLETED);
            } else {
                qw.eq(PmRoutineTask::getStatus, status.toUpperCase(Locale.ROOT));
            }
        }
        List<PmRoutineTask> tasks = taskMapper.selectList(qw);
        if (tasks.isEmpty()) return List.of();
        List<Long> taskIds = tasks.stream().map(PmRoutineTask::getId).toList();
        Map<Long, List<PmRoutineTaskItem>> itemMap = taskItemMapper.selectList(
                new LambdaQueryWrapper<PmRoutineTaskItem>()
                        .in(PmRoutineTaskItem::getTaskId, taskIds)
                        .orderByAsc(PmRoutineTaskItem::getSeqNo)
                        .orderByAsc(PmRoutineTaskItem::getId))
                .stream().collect(Collectors.groupingBy(PmRoutineTaskItem::getTaskId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PmRoutineTask t : tasks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("task", t);
            m.put("items", itemMap.getOrDefault(t.getId(), List.of()));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getTaskDetail(Long taskId) {
        PmRoutineTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException("巡检任务不存在");
        List<PmRoutineTaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<PmRoutineTaskItem>()
                .eq(PmRoutineTaskItem::getTaskId, taskId)
                .orderByAsc(PmRoutineTaskItem::getSeqNo)
                .orderByAsc(PmRoutineTaskItem::getId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task", task);
        m.put("items", items);
        return m;
    }

    /**
     * 检查项即时拍照打卡：
     * - 严格校验必须携带即时拍摄并上传的带水印照片（fileId > 0 且张数 >= minPhotos）；
     * - 严禁绕过拍照直接标完成；
     * - 当该巡检任务下全部预设检查项都完成拍照后，系统自动将任务置为 COMPLETED！
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitItemCapture(Long taskId, Long itemId, CaptureItemReq req) {
        PmRoutineTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BizException("巡检任务不存在");
        PmRoutineTaskItem item = taskItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getTaskId(), taskId)) {
            throw new BizException("巡检检查项不存在");
        }

        List<PhotoProof> validPhotos = req == null || req.photos() == null ? List.of()
                : req.photos().stream().filter(p -> p != null && p.fileId() != null && p.fileId() > 0).toList();
        int minNeed = item.getMinPhotos() == null || item.getMinPhotos() < 1 ? 1 : item.getMinPhotos();
        if (validPhotos.size() < minNeed) {
            throw new BizException("本检查项必须现场即时拍照并上传至少 " + minNeed + " 张带水印照片，不可手动跳过完成");
        }

        String result = "ABNORMAL".equalsIgnoreCase(req.result()) ? "ABNORMAL" : "NORMAL";
        if ("ABNORMAL".equals(result) && !StringUtils.hasText(req.issueDesc())) {
            throw new BizException("发现异常时请简要填写问题描述");
        }

        String fileIds = validPhotos.stream().map(p -> String.valueOf(p.fileId())).collect(Collectors.joining(","));
        String urlsJson;
        try {
            urlsJson = objectMapper.writeValueAsString(validPhotos);
        } catch (Exception e) {
            urlsJson = "[]";
        }

        LocalDateTime now = LocalDateTime.now();
        item.setChecked(1);
        item.setResult(result);
        item.setIssueCategory("ABNORMAL".equals(result) ? nvl(req.issueCategory(), "其他异常") : "");
        item.setIssueDesc("ABNORMAL".equals(result) ? req.issueDesc().trim() : "");
        item.setPhotoFileIds(fileIds);
        item.setPhotoUrlsJson(urlsJson);
        item.setCapturedBy(StringUtils.hasText(req.capturedBy()) ? req.capturedBy().trim() : nvl(task.getInspectorName()));
        item.setCapturedTime(now);
        taskItemMapper.updateById(item);

        // 重新统计全任务进度，仅当全部检查项均已拍照完成后自动标为 COMPLETED
        List<PmRoutineTaskItem> allItems = taskItemMapper.selectList(new LambdaQueryWrapper<PmRoutineTaskItem>()
                .eq(PmRoutineTaskItem::getTaskId, taskId)
                .orderByAsc(PmRoutineTaskItem::getSeqNo)
                .orderByAsc(PmRoutineTaskItem::getId));
        int total = allItems.size();
        int done = (int) allItems.stream().filter(i -> Integer.valueOf(1).equals(i.getChecked())).count();
        int issues = (int) allItems.stream()
                .filter(i -> Integer.valueOf(1).equals(i.getChecked()) && "ABNORMAL".equalsIgnoreCase(i.getResult()))
                .count();

        task.setTotalItems(total);
        task.setCompletedItems(done);
        task.setIssueCount(issues);
        if (total > 0 && done >= total) {
            task.setStatus(PmRoutineTask.ST_COMPLETED);
            if (task.getCompletedTime() == null) task.setCompletedTime(now);
        } else if (task.getDeadlineTime() != null && now.isAfter(task.getDeadlineTime())) {
            task.setStatus(PmRoutineTask.ST_OVERDUE);
        } else {
            task.setStatus(done > 0 ? PmRoutineTask.ST_IN_PROGRESS : PmRoutineTask.ST_PENDING);
        }
        taskMapper.updateById(task);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task", task);
        out.put("items", allItems);
        out.put("autoCompleted", PmRoutineTask.ST_COMPLETED.equals(task.getStatus()));
        return out;
    }

    /** 发现异常的巡检项一键转物业报修工单，实现闭环整改 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createWorkOrderFromItem(Long itemId) {
        PmRoutineTaskItem item = taskItemMapper.selectById(itemId);
        if (item == null) throw new BizException("检查项不存在");
        if (!"ABNORMAL".equalsIgnoreCase(item.getResult())) {
            throw new BizException("仅标记为「发现异常」的检查项可转报修工单");
        }
        if (item.getWorkOrderId() != null && StringUtils.hasText(item.getWorkOrderCode())) {
            return Map.of("workOrderId", item.getWorkOrderId(), "workOrderCode", item.getWorkOrderCode(), "existed", true);
        }
        PmRoutineTask task = taskMapper.selectById(item.getTaskId());
        String modLabel = moduleLabel(task != null ? task.getModuleType() : "EQUIPMENT");

        WorkOrder wo = new WorkOrder();
        wo.setCode("GD" + System.currentTimeMillis());
        wo.setOrderType("巡检");
        wo.setTitle(modLabel + "异常：" + item.getItemName());
        wo.setLocation(StringUtils.hasText(item.getAreaPoint()) ? item.getAreaPoint() : (task != null ? task.getArea() : ""));
        wo.setCategory(moduleCategory(task != null ? task.getModuleType() : "EQUIPMENT"));
        wo.setUrgency(2);
        wo.setStatus(1);
        wo.setSource(modLabel);
        wo.setContact(nvl(item.getCapturedBy(), task != null ? task.getInspectorName() : ""));
        wo.setContactPhone(task != null ? nvl(task.getInspectorPhone()) : "");
        wo.setImages(nvl(item.getPhotoFileIds()));
        wo.setRemark("巡检任务：" + (task != null ? task.getTemplateName() + "（" + task.getTaskDate() + " " + task.getSlotLabel() + "）" : "")
                + "；巡检人：" + nvl(item.getCapturedBy())
                + "；异常分类：" + nvl(item.getIssueCategory())
                + "；问题描述：" + nvl(item.getIssueDesc()));
        workOrderMapper.insert(wo);

        item.setWorkOrderId(wo.getId());
        item.setWorkOrderCode(wo.getCode());
        taskItemMapper.updateById(item);
        return Map.of("workOrderId", wo.getId(), "workOrderCode", wo.getCode(), "existed", false);
    }

    // ------------------------------------------------------------------ 可视化统计分析（日/月/年 + 巡检人 + 问题分析）

    public Map<String, Object> analytics(String moduleType, String granularity, LocalDate anchorDate) {
        String mod = "ALL".equalsIgnoreCase(moduleType) ? "ALL" : normalizeModule(moduleType);
        String gran = StringUtils.hasText(granularity) ? granularity.toUpperCase(Locale.ROOT) : "MONTH";
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        refreshOverdueStatus(LocalDateTime.now());

        LocalDate start;
        LocalDate end;
        if ("DAY".equals(gran)) {
            start = anchor;
            end = anchor;
        } else if ("YEAR".equals(gran)) {
            start = LocalDate.of(anchor.getYear(), 1, 1);
            end = LocalDate.of(anchor.getYear(), 12, 31);
        } else {
            gran = "MONTH";
            YearMonth ym = YearMonth.from(anchor);
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        }

        LambdaQueryWrapper<PmRoutineTask> qw = new LambdaQueryWrapper<PmRoutineTask>()
                .ge(PmRoutineTask::getTaskDate, start)
                .le(PmRoutineTask::getTaskDate, end)
                .orderByAsc(PmRoutineTask::getTaskDate)
                .orderByAsc(PmRoutineTask::getScheduledStart);
        if (!"ALL".equals(mod)) {
            qw.eq(PmRoutineTask::getModuleType, mod);
        }
        List<PmRoutineTask> tasks = taskMapper.selectList(qw);

        List<Long> taskIds = tasks.stream().map(PmRoutineTask::getId).toList();
        List<PmRoutineTaskItem> abnormalItems = taskIds.isEmpty() ? List.of() :
                taskItemMapper.selectList(new LambdaQueryWrapper<PmRoutineTaskItem>()
                        .in(PmRoutineTaskItem::getTaskId, taskIds)
                        .eq(PmRoutineTaskItem::getChecked, 1)
                        .eq(PmRoutineTaskItem::getResult, "ABNORMAL")
                        .orderByDesc(PmRoutineTaskItem::getCapturedTime));
        Map<Long, PmRoutineTask> taskById = tasks.stream().collect(Collectors.toMap(PmRoutineTask::getId, t -> t));

        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> PmRoutineTask.ST_COMPLETED.equals(t.getStatus())).count();
        int overdueTasks = (int) tasks.stream().filter(t -> PmRoutineTask.ST_OVERDUE.equals(t.getStatus())).count();
        int incompleteTasks = totalTasks - completedTasks;
        int totalItems = tasks.stream().mapToInt(t -> t.getTotalItems() == null ? 0 : t.getTotalItems()).sum();
        int completedItems = tasks.stream().mapToInt(t -> t.getCompletedItems() == null ? 0 : t.getCompletedItems()).sum();
        int issueCount = abnormalItems.size();
        int workOrderCount = (int) abnormalItems.stream().filter(i -> i.getWorkOrderId() != null).count();
        double rate = totalTasks == 0 ? 0.0 : Math.round((completedTasks * 1000.0) / totalTasks) / 10.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startDate", start.toString());
        summary.put("endDate", end.toString());
        summary.put("granularity", gran);
        summary.put("totalTasks", totalTasks);
        summary.put("completedTasks", completedTasks);
        summary.put("incompleteTasks", incompleteTasks);
        summary.put("overdueTasks", overdueTasks);
        summary.put("completionRate", rate);
        summary.put("totalItems", totalItems);
        summary.put("completedItems", completedItems);
        summary.put("issueCount", issueCount);
        summary.put("workOrderCount", workOrderCount);

        // 1) 趋势数据（按日时段 / 按月每日 / 按年每月）
        List<Map<String, Object>> trend = buildTrend(tasks, gran, start, end);

        // 2) 巡检人执行情况汇总
        Map<String, List<PmRoutineTask>> byIns = new LinkedHashMap<>();
        for (PmRoutineTask t : tasks) {
            String key = StringUtils.hasText(t.getInspectorName()) ? t.getInspectorName() : "未指派";
            byIns.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> inspectorStats = new ArrayList<>();
        for (Map.Entry<String, List<PmRoutineTask>> e : byIns.entrySet()) {
            List<PmRoutineTask> list = e.getValue();
            PmRoutineTask sample = list.get(0);
            int tot = list.size();
            int done = (int) list.stream().filter(t -> PmRoutineTask.ST_COMPLETED.equals(t.getStatus())).count();
            int ov = (int) list.stream().filter(t -> PmRoutineTask.ST_OVERDUE.equals(t.getStatus())).count();
            int iss = list.stream().mapToInt(t -> t.getIssueCount() == null ? 0 : t.getIssueCount()).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("inspectorId", sample.getInspectorId());
            row.put("inspectorName", e.getKey());
            row.put("position", nvl(sample.getInspectorPosition()));
            row.put("phone", nvl(sample.getInspectorPhone()));
            row.put("total", tot);
            row.put("completed", done);
            row.put("incomplete", tot - done);
            row.put("overdue", ov);
            row.put("issues", iss);
            row.put("rate", tot == 0 ? 0.0 : Math.round((done * 1000.0) / tot) / 10.0);
            inspectorStats.add(row);
        }
        inspectorStats.sort((a, b) -> Double.compare((double) b.get("rate"), (double) a.get("rate")));

        // 3) 巡检问题分析（按分类 & 按高频区域 & 明细）
        Map<String, Long> catCounts = abnormalItems.stream()
                .collect(Collectors.groupingBy(i -> StringUtils.hasText(i.getIssueCategory()) ? i.getIssueCategory() : "其他异常",
                        LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> issuesByCategory = catCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> Map.<String, Object>of("category", e.getKey(), "count", e.getValue()))
                .toList();

        Map<String, Long> areaCounts = abnormalItems.stream()
                .collect(Collectors.groupingBy(i -> StringUtils.hasText(i.getAreaPoint()) ? i.getAreaPoint() : "未填区域",
                        LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> issuesByArea = areaCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.<String, Object>of("area", e.getKey(), "count", e.getValue()))
                .toList();

        List<Map<String, Object>> recentIssues = abnormalItems.stream().limit(30).map(i -> {
            PmRoutineTask pt = taskById.get(i.getTaskId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", i.getId());
            m.put("taskId", i.getTaskId());
            m.put("taskDate", pt != null ? pt.getTaskDate().toString() : "");
            m.put("slotLabel", pt != null ? pt.getSlotLabel() : "");
            m.put("templateName", pt != null ? pt.getTemplateName() : "");
            m.put("itemName", i.getItemName());
            m.put("areaPoint", i.getAreaPoint());
            m.put("issueCategory", i.getIssueCategory());
            m.put("issueDesc", i.getIssueDesc());
            m.put("capturedBy", i.getCapturedBy());
            m.put("capturedTime", i.getCapturedTime());
            m.put("photoUrlsJson", i.getPhotoUrlsJson());
            m.put("workOrderId", i.getWorkOrderId());
            m.put("workOrderCode", i.getWorkOrderCode());
            return m;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("trend", trend);
        out.put("byInspector", inspectorStats);
        out.put("issuesByCategory", issuesByCategory);
        out.put("issuesByArea", issuesByArea);
        out.put("recentIssues", recentIssues);
        return out;
    }

    private List<Map<String, Object>> buildTrend(List<PmRoutineTask> tasks, String gran, LocalDate start, LocalDate end) {
        List<Map<String, Object>> list = new ArrayList<>();
        if ("DAY".equals(gran)) {
            Map<String, List<PmRoutineTask>> bySlot = new LinkedHashMap<>();
            for (PmRoutineTask t : tasks) {
                bySlot.computeIfAbsent(t.getSlotLabel(), k -> new ArrayList<>()).add(t);
            }
            for (Map.Entry<String, List<PmRoutineTask>> e : bySlot.entrySet()) {
                list.add(trendPoint(e.getKey(), e.getValue()));
            }
            return list;
        }
        if ("YEAR".equals(gran)) {
            Map<Integer, List<PmRoutineTask>> byMonth = tasks.stream()
                    .collect(Collectors.groupingBy(t -> t.getTaskDate().getMonthValue()));
            for (int m = 1; m <= 12; m++) {
                list.add(trendPoint(m + "月", byMonth.getOrDefault(m, List.of())));
            }
            return list;
        }
        // MONTH：逐日汇总
        Map<LocalDate, List<PmRoutineTask>> byDay = tasks.stream()
                .collect(Collectors.groupingBy(PmRoutineTask::getTaskDate));
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            list.add(trendPoint(d.getDayOfMonth() + "日", byDay.getOrDefault(d, List.of())));
        }
        return list;
    }

    private static Map<String, Object> trendPoint(String label, List<PmRoutineTask> bucket) {
        int tot = bucket.size();
        int done = (int) bucket.stream().filter(t -> PmRoutineTask.ST_COMPLETED.equals(t.getStatus())).count();
        int ov = (int) bucket.stream().filter(t -> PmRoutineTask.ST_OVERDUE.equals(t.getStatus())).count();
        int iss = bucket.stream().mapToInt(t -> t.getIssueCount() == null ? 0 : t.getIssueCount()).sum();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("label", label);
        p.put("total", tot);
        p.put("completed", done);
        p.put("incomplete", tot - done);
        p.put("overdue", ov);
        p.put("issues", iss);
        p.put("rate", tot == 0 ? 0.0 : Math.round((done * 1000.0) / tot) / 10.0);
        return p;
    }

    // ------------------------------------------------------------------ 辅助方法

    private static String normalizeModule(String moduleType) {
        if (!StringUtils.hasText(moduleType)) return "EQUIPMENT";
        String u = moduleType.trim().toUpperCase(Locale.ROOT);
        if (MODULES.contains(u)) return u;
        if (moduleType.contains("安防") || moduleType.contains("巡更")) return "PATROL";
        if (moduleType.contains("保洁")) return "CLEANING";
        if (moduleType.contains("绿化")) return "GREENING";
        return "EQUIPMENT";
    }

    private static String moduleLabel(String mod) {
        return switch (normalizeModule(mod)) {
            case "PATROL" -> "安防巡更";
            case "CLEANING" -> "保洁检查";
            case "GREENING" -> "绿化检查";
            default -> "设备巡检";
        };
    }

    private static String moduleCategory(String mod) {
        return switch (normalizeModule(mod)) {
            case "PATROL" -> "安防";
            case "CLEANING" -> "保洁";
            case "GREENING" -> "绿化";
            default -> "设施设备";
        };
    }

    private static String validHm(String v, String def) {
        if (!StringUtils.hasText(v)) return def;
        return parseMinutes(v, -1) >= 0 ? v.trim() : def;
    }

    private static String validEndHm(String v, String def) {
        if (!StringUtils.hasText(v)) return def;
        String s = v.trim();
        if ("24:00".equals(s)) return "24:00";
        return parseMinutes(s, -1) >= 0 ? s : def;
    }

    private static int parseMinutes(String hm, int fallback) {
        if (!StringUtils.hasText(hm)) return fallback;
        String[] parts = hm.trim().split(":");
        if (parts.length != 2) return fallback;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h >= 0 && h < 24 && m >= 0 && m < 60) return h * 60 + m;
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    private static int parseEndMinutes(String hm, int fallback) {
        if ("24:00".equals(hm == null ? "" : hm.trim())) return 24 * 60;
        return parseMinutes(hm, fallback);
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nvl(String s, String def) {
        return StringUtils.hasText(s) ? s.trim() : def;
    }
}
