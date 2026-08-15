package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单汇总:把原先散在 /stats、/dashboard/workbench、/overview、/workorder-category
 * 四处且口径互不一致的统计收敛到一处。
 *
 * <p>原 /stats 只数状态 1/3/5,漏 2/4/6/7,导致各项之和不等于 total。
 * 这里统一先按状态分组全量计数,其余口径都由同一批数据派生,保证可加总。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkOrderSummaryService {

    private final WorkOrderMapper workOrderMapper;

    private static final Map<Integer, String> STATUS_LABEL = Map.of(
            WorkOrderService.ST_PENDING_DISPATCH, "待派单",
            WorkOrderService.ST_PENDING_ACCEPT, "待接单",
            WorkOrderService.ST_PROCESSING, "处理中",
            WorkOrderService.ST_PENDING_VERIFY, "待验收",
            WorkOrderService.ST_DONE, "已完成",
            WorkOrderService.ST_CLOSED, "已关闭",
            WorkOrderService.ST_TIMEOUT, "已超时");

    /** 按状态分组计数。返回的 map 只含实际出现过的状态。 */
    public Map<Integer, Long> countByStatus(Long projectId) {
        return load(projectId, null).stream()
                .filter(w -> w.getStatus() != null)
                .collect(Collectors.groupingBy(WorkOrder::getStatus, Collectors.counting()));
    }

    /**
     * 兼容原 /stats 的返回结构,但补齐全部状态且保证 total 等于各状态之和。
     * pending 语义由"仅待派单"改为"所有未结束",与首页卡片口径对齐。
     */
    public Map<String, Object> toStatsMap(Map<Integer, Long> byStatus) {
        Map<String, Object> m = new HashMap<>();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        m.put("total", total);
        m.put("pendingDispatch", byStatus.getOrDefault(WorkOrderService.ST_PENDING_DISPATCH, 0L));
        m.put("pendingAccept", byStatus.getOrDefault(WorkOrderService.ST_PENDING_ACCEPT, 0L));
        m.put("processing", byStatus.getOrDefault(WorkOrderService.ST_PROCESSING, 0L));
        m.put("pendingVerify", byStatus.getOrDefault(WorkOrderService.ST_PENDING_VERIFY, 0L));
        m.put("done", byStatus.getOrDefault(WorkOrderService.ST_DONE, 0L));
        m.put("closed", byStatus.getOrDefault(WorkOrderService.ST_CLOSED, 0L));
        m.put("timeout", byStatus.getOrDefault(WorkOrderService.ST_TIMEOUT, 0L));
        // 在办 = 1+2+3+4,首页"待处理"应取这个
        long open = WorkOrderService.ST_OPEN.stream()
                .mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
        m.put("open", open);
        // 兼容旧前端字段名:pending 原指待派单,现改为在办总数,避免首页与列表数字打架
        m.put("pending", open);
        return m;
    }

    /** 汇总总览。days 为近 N 天的时间窗,用于趋势与 SLA 达成率。 */
    public Map<String, Object> summary(Long projectId, int days) {
        List<WorkOrder> all = load(projectId, null);
        Map<Integer, Long> byStatus = all.stream()
                .filter(w -> w.getStatus() != null)
                .collect(Collectors.groupingBy(WorkOrder::getStatus, Collectors.counting()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", toStatsMap(byStatus));
        out.put("byStatus", labeled(byStatus, s -> STATUS_LABEL.getOrDefault(s, "未知(" + s + ")")));
        out.put("bySource", groupText(all, WorkOrder::getSource, "未标注"));
        out.put("byOrderType", groupText(all, WorkOrder::getOrderType, "未分类"));
        out.put("byCategory", groupText(all, WorkOrder::getCategory, "未分类"));
        out.put("byUrgency", groupText(all,
                w -> w.getUrgency() == null ? null : urgencyLabel(w.getUrgency()), "未设置"));

        // SLA:只对已进入终态的单计算达成率,在办单还没有结论
        List<WorkOrder> settled = all.stream()
                .filter(w -> w.getStatus() != null
                        && (w.getStatus() == WorkOrderService.ST_DONE
                            || w.getStatus() == WorkOrderService.ST_CLOSED))
                .toList();
        long breached = settled.stream()
                .filter(w -> w.getSlaState() != null && w.getSlaState() > 0)
                .count();
        Map<String, Object> sla = new LinkedHashMap<>();
        // 统一用 long,避免 settled(int) 与 breached(long) 混用导致前端/断言类型不一致
        long settledCount = settled.size();
        sla.put("settled", settledCount);
        sla.put("breached", breached);
        sla.put("met", settledCount - breached);
        sla.put("metRate", settled.isEmpty() ? null
                : Math.round((settled.size() - breached) * 1000.0 / settled.size()) / 10.0);
        // 在办单里已被标记超时的,需要立刻干预
        sla.put("openTimeout", all.stream()
                .filter(w -> w.getStatus() != null && WorkOrderService.ST_OPEN.contains(w.getStatus()))
                .filter(w -> w.getSlaState() != null && w.getSlaState() > 0)
                .count());
        out.put("sla", sla);

        // 近 N 天新增趋势(按天)
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        Map<String, Long> trend = all.stream()
                .filter(w -> w.getCreateTime() != null && w.getCreateTime().isAfter(from))
                .collect(Collectors.groupingBy(
                        w -> w.getCreateTime().toLocalDate().toString(),
                        LinkedHashMap::new, Collectors.counting()));
        out.put("trend", trend.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Map.of("date", e.getKey(), "count", e.getValue()))
                .toList());
        out.put("days", days);
        return out;
    }

    private List<WorkOrder> load(Long projectId, Integer status) {
        LambdaQueryWrapper<WorkOrder> q = new LambdaQueryWrapper<>();
        if (projectId != null) {
            q.eq(WorkOrder::getProjectId, projectId);
        }
        if (status != null) {
            q.eq(WorkOrder::getStatus, status);
        }
        return workOrderMapper.selectList(q);
    }

    private static String urgencyLabel(int u) {
        return switch (u) {
            case 1 -> "低";
            case 2 -> "中";
            case 3 -> "高";
            default -> "未知(" + u + ")";
        };
    }

    private List<Map<String, Object>> labeled(Map<Integer, Long> counts,
                                              Function<Integer, String> labeller) {
        List<Map<String, Object>> list = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", e.getKey());
                    row.put("name", labeller.apply(e.getKey()));
                    row.put("count", e.getValue());
                    list.add(row);
                });
        return list;
    }

    /** 按文本列分组;空值归入 fallback,倒序返回,便于前端直接画条形图。 */
    private List<Map<String, Object>> groupText(List<WorkOrder> all,
                                                Function<WorkOrder, String> getter,
                                                String fallback) {
        return all.stream()
                .collect(Collectors.groupingBy(w -> {
                    String v = getter.apply(w);
                    return (v == null || v.isBlank()) ? fallback : v;
                }, Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
