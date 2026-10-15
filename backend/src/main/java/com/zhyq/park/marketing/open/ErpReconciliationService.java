package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.finance.MktServiceFeeBillService;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** External ERP export versus accepted outbound orders. No fabricated supplier API or money estimate. */
@Service
@RequiredArgsConstructor
public class ErpReconciliationService {
    public static final String METRIC = "VALID_OUTBOUND_ORDER_COUNT";
    public static final String SCOPE = "本云仓、所选发货日期内、系统登记客户的有效出库单数（唯一出库单号）；包含手工补单和云仓直签，排除退款、取消和沙箱。不是件数、包裹数或含自有客户的全仓发货量。";
    private final MktWarehouseMapper warehouseMapper;
    private final MktReferralOrderMapper orderMapper;
    private final ErpReconciliationQueryMapper queryMapper;
    private final MktCustomerErpMapMapper mappingMapper;
    private final MktErpReconcileSnapshotMapper snapshotMapper;
    private final MktAuditService audit;
    private final ObjectMapper json;

    public record Submission(LocalDate periodStart, LocalDate periodEnd, Long externalOrderCount,
                             String sourceName, String evidence) { }
    public record Calculation(long systemOrderCount, long cancelledOrderCount, int unmappedCount,
                              int retryCount, int pendingCount, int blockingCount,
                              String fingerprint, List<String> blockingReasons) { }
    public record SnapshotView(MktErpReconcileSnapshot snapshot, boolean stale, boolean canConfirm) { }
    public record Preview(String metric, String scope, Calculation calculation, List<SnapshotView> history) { }
    public record Period(Long warehouseId, LocalDate periodStart, LocalDate periodEnd) { }

    @Transactional(readOnly = true)
    public Preview preview(Long warehouseId, LocalDate start, LocalDate end) {
        requireWarehouse(warehouseId, false); validatePeriod(start, end);
        var current = calculate(warehouseId, start, end);
        var rows = history(warehouseId, start, end);
        var views = rows.stream().map(s -> new SnapshotView(s,
                !Objects.equals(s.getDataFingerprint(), current.fingerprint()),
                "DRAFT".equals(s.getState()) && s == rows.get(0) && ready(s, current))).toList();
        return new Preview(METRIC, SCOPE, current, views);
    }

    /** Every submitted correction creates a new revision; no recorded numbers are overwritten. */
    @Transactional
    public MktErpReconcileSnapshot submit(Long warehouseId, Submission input) {
        var warehouse = requireWarehouse(warehouseId, true);
        validateSubmission(input);
        var current = calculate(warehouseId, input.periodStart(), input.periodEnd());
        var snapshot = create(warehouse, input, current, latest(warehouseId, input.periodStart(), input.periodEnd()), "DRAFT", null, null);
        audit.log("erp.reconciliation.submit", "erp_reconcile", snapshot.getId(), "外部出库单数 " + input.externalOrderCount() + "，系统 " + current.systemOrderCount());
        return snapshot;
    }

    /** Confirm is deliberately strict: even a one-order difference blocks settlement. */
    @Transactional
    public MktErpReconcileSnapshot confirm(Long warehouseId, Long snapshotId, String reason, String operator) {
        var warehouse = requireWarehouse(warehouseId, true);
        requireText(reason, 500, "确认说明");
        var draft = requireOwned(warehouseId, snapshotId);
        var latest = latest(warehouseId, draft.getPeriodStart(), draft.getPeriodEnd());
        if (!Objects.equals(latest.getId(), draft.getId()) || !"DRAFT".equals(draft.getState()))
            throw new BizException("只能确认最新草稿，请刷新对账记录");
        var current = calculate(warehouseId, draft.getPeriodStart(), draft.getPeriodEnd());
        if (!Objects.equals(current.fingerprint(), draft.getDataFingerprint()))
            throw new BizException("订单、映射或事件处置已变化，请刷新计算并重新保存草稿");
        if (!ready(draft, current)) throw new BizException("对账存在单量差异或待处理事件，须解决后重新提交，不能直接放行结算");
        var confirmed = create(warehouse, submission(draft), current, draft, "CONFIRMED", reason.trim(), operator);
        audit.log("erp.reconciliation.confirm", "erp_reconcile", confirmed.getId(), reason.trim());
        return confirmed;
    }

    /** Called while the same warehouse row is locked by settlement/ingestion. */
    public MktErpReconcileSnapshot requireConfirmed(Long warehouseId, LocalDate start, LocalDate end) {
        var snapshot = latest(warehouseId, start, end);
        if (snapshot == null || !METRIC.equals(snapshot.getMetric()) || !"CONFIRMED".equals(snapshot.getState()))
            throw new BizException("本期 ERP 对账尚未确认，请在 ERP 对接中录入同口径外部单数并完成对账");
        assertCurrent(snapshot);
        return snapshot;
    }

    public void assertCurrent(MktErpReconcileSnapshot snapshot) {
        if (snapshot == null || !METRIC.equals(snapshot.getMetric()) || !"CONFIRMED".equals(snapshot.getState()))
            throw new BizException("结算缺少有效的 ERP 对账确认版本");
        var current = calculate(snapshot.getWarehouseId(), snapshot.getPeriodStart(), snapshot.getPeriodEnd());
        if (!ready(snapshot, current)) throw new BizException("对账确认后订单、映射或事件已变化，结算已暂停；请重新对账并处理结算差异");
    }

    public MktErpReconcileSnapshot requireOwned(Long warehouseId, Long snapshotId) {
        var s = snapshotMapper.selectById(snapshotId);
        if (s == null || !Objects.equals(s.getWarehouseId(), warehouseId)) throw new BizException(403, "对账记录不存在或不属于此云仓");
        return s;
    }

    public List<Period> periodsToRefresh() {
        Map<Period, MktErpReconcileSnapshot> latest = new LinkedHashMap<>();
        for (var s : snapshotMapper.selectList(new LambdaQueryWrapper<MktErpReconcileSnapshot>()
                .eq(MktErpReconcileSnapshot::getMetric, METRIC).orderByDesc(MktErpReconcileSnapshot::getId))) {
            latest.putIfAbsent(new Period(s.getWarehouseId(), s.getPeriodStart(), s.getPeriodEnd()), s);
        }
        return new ArrayList<>(latest.keySet());
    }

    /** Changes make a new unconfirmed draft. The original confirmed/paid evidence remains untouched. */
    @Transactional
    public void refreshPeriod(Long warehouseId, LocalDate start, LocalDate end) {
        var warehouse = requireWarehouse(warehouseId, true);
        var previous = latest(warehouseId, start, end);
        if (previous == null || !METRIC.equals(previous.getMetric())) return;
        var current = calculate(warehouseId, start, end);
        if (Objects.equals(previous.getDataFingerprint(), current.fingerprint())) return;
        var next = create(warehouse, submission(previous), current, previous, "DRAFT", "数据变化，需重新核对外部导出并确认", null);
        audit.log("erp.reconciliation.refresh", "erp_reconcile", next.getId(), "生成待确认的新版本，历史版本保留");
    }

    public Calculation calculate(Long warehouseId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        var orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getWarehouseId, warehouseId).eq(MktReferralOrder::getSourceType, 2)
                .ge(MktReferralOrder::getEventTime, start.atStartOfDay()).lt(MktReferralOrder::getEventTime, end.plusDays(1).atStartOfDay())
                .orderByAsc(MktReferralOrder::getId));
        // Terminal/retry events after the period still affect orders shipped within that period.
        var events = queryMapper.periodEvents(warehouseId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        var mappings = mappingMapper.selectList(new LambdaQueryWrapper<MktCustomerErpMap>()
                .eq(MktCustomerErpMap::getWarehouseId, warehouseId).orderByAsc(MktCustomerErpMap::getId));
        var unresolved = queryMapper.periodUnmapped(warehouseId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Set<Long> unmappedIds = new HashSet<>();
        for (var row : unresolved) unmappedIds.add(row.getInboxId());
        int retries = 0, pending = 0;
        List<String> reasons = new ArrayList<>();
        for (var event : events) {
            if ("WAITING_MAPPING".equals(event.getStatus()) || "UNMAPPED".equals(event.getStatus()) || "UNMAPPED".equals(event.getLastError())) unmappedIds.add(event.getId());
            else if (Set.of("RETRY", "DEAD").contains(Objects.toString(event.getStatus(), ""))) retries++;
            else if (!"PROCESSED".equals(event.getStatus())) pending++;
        }
        int pendingOrders = (int) orders.stream().filter(o -> !Set.of(2, 3, 4).contains(o.getStatus() == null ? 0 : o.getStatus())).count();
        long active = orders.stream().filter(o -> Integer.valueOf(2).equals(o.getStatus())).count();
        long cancelled = orders.stream().filter(o -> Integer.valueOf(3).equals(o.getStatus()) || Integer.valueOf(4).equals(o.getStatus())).count();
        if (!unmappedIds.isEmpty()) reasons.add("待匹配或待明确归属事件 " + unmappedIds.size() + " 条");
        if (retries > 0) reasons.add("待重试/死信事件 " + retries + " 条");
        if (pending > 0) reasons.add("待处理事件 " + pending + " 条（含可能影响本期订单的撤单/退款）");
        if (pendingOrders > 0) reasons.add("尚未确认或无归属出库单 " + pendingOrders + " 条");
        var tokens = new ArrayList<Object>();
        tokens.add(Arrays.asList(METRIC, warehouseId, start.toString(), end.toString()));
        for (var o : orders) tokens.add(Arrays.asList("order", o.getId(), o.getSourceNo(), o.getSourceId(), o.getCustomerId(), o.getCustomerCode(),
                o.getStatus(), Objects.toString(o.getEventTime(), ""), o.getQty(), o.getPackages(), o.getServiceFee(), o.getPoolAmount(), o.getLogisticsNo()));
        for (var e : events) tokens.add(Arrays.asList("event", e.getId(), e.getEventId(), e.getEvent(), e.getOrderNo(), e.getStatus(), e.getPayloadDigest(), e.getLastError()));
        for (var m : mappings) tokens.add(Arrays.asList("mapping", m.getId(), m.getCustomerId(), m.getCustomerCode(), m.getStatus()));
        for (var u : unresolved) tokens.add(Arrays.asList("unmapped", u.getId(), u.getInboxId(), u.getCustomerCode(), u.getStatus()));
        return new Calculation(active, cancelled, unmappedIds.size(), retries, pending + pendingOrders,
                unmappedIds.size() + retries + pending + pendingOrders, fingerprint(tokens), reasons);
    }

    static boolean ready(MktErpReconcileSnapshot s, Calculation c) {
        return s.getExternalOrderCount() != null && s.getExternalOrderCount() == c.systemOrderCount()
                && c.blockingCount() == 0 && Objects.equals(s.getDataFingerprint(), c.fingerprint())
                && s.getSourceName() != null && !s.getSourceName().isBlank() && s.getEvidence() != null && !s.getEvidence().isBlank();
    }

    private MktErpReconcileSnapshot create(MktWarehouse warehouse, Submission input, Calculation c,
                                           MktErpReconcileSnapshot previous, String state, String reason, String operator) {
        var s = new MktErpReconcileSnapshot();
        s.setWarehouseId(warehouse.getId()); s.setProjectId(warehouse.getProjectId());
        s.setPeriodStart(input.periodStart()); s.setPeriodEnd(input.periodEnd()); s.setMetric(METRIC); s.setState(state);
        s.setRevision(previous == null ? 1 : previous.getRevision() + 1); s.setPreviousSnapshotId(previous == null ? null : previous.getId());
        s.setExternalOrderCount(input.externalOrderCount()); s.setSystemOrderCount(c.systemOrderCount());
        s.setDiffOrderCount(input.externalOrderCount() - c.systemOrderCount()); s.setBlockingCount(c.blockingCount());
        s.setSourceName(input.sourceName().trim()); s.setEvidence(input.evidence().trim());
        s.setDataFingerprint(c.fingerprint()); s.setDetailsJson(serialize(c));
        s.setFrozen("CONFIRMED".equals(state) ? 0 : 1); s.setReason(reason);
        if ("CONFIRMED".equals(state)) { s.setConfirmedBy(operator); s.setConfirmedAt(LocalDateTime.now()); }
        s.setExpectedAmount(BigDecimal.ZERO); s.setActualAmount(BigDecimal.ZERO); s.setDiffAmount(BigDecimal.ZERO); s.setDiffRatio(BigDecimal.ZERO);
        snapshotMapper.insert(s);
        return s;
    }

    private List<MktErpReconcileSnapshot> history(Long wh, LocalDate start, LocalDate end) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<MktErpReconcileSnapshot>()
                .eq(MktErpReconcileSnapshot::getWarehouseId, wh).eq(MktErpReconcileSnapshot::getPeriodStart, start)
                .eq(MktErpReconcileSnapshot::getPeriodEnd, end).orderByDesc(MktErpReconcileSnapshot::getRevision));
    }
    private MktErpReconcileSnapshot latest(Long wh, LocalDate start, LocalDate end) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<MktErpReconcileSnapshot>()
                .eq(MktErpReconcileSnapshot::getWarehouseId, wh).eq(MktErpReconcileSnapshot::getPeriodStart, start)
                .eq(MktErpReconcileSnapshot::getPeriodEnd, end).orderByDesc(MktErpReconcileSnapshot::getRevision).last("limit 1"));
    }
    private MktWarehouse requireWarehouse(Long warehouseId, boolean lock) {
        var w = warehouseMapper.selectOne(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getId, warehouseId).last(lock ? "FOR UPDATE" : ""));
        if (w == null) throw new BizException(404, "云仓不存在");
        return w;
    }
    private static Submission submission(MktErpReconcileSnapshot s) {
        return new Submission(s.getPeriodStart(), s.getPeriodEnd(), s.getExternalOrderCount(), s.getSourceName(), s.getEvidence());
    }
    private static void validateSubmission(Submission input) {
        if (input == null) throw new BizException("对账资料不能为空");
        validatePeriod(input.periodStart(), input.periodEnd());
        if (input.externalOrderCount() == null || input.externalOrderCount() < 0 || input.externalOrderCount() > 1_000_000_000L)
            throw new BizException("请填写 ERP 导出中的有效出库单数（0至10亿整数）");
        requireText(input.sourceName(), 200, "外部数据来源"); requireText(input.evidence(), 1000, "凭据及筛选口径说明");
    }
    private static void validatePeriod(LocalDate start, LocalDate end) {
        MktServiceFeeBillService.validatePeriod(start, end);
        if (ChronoUnit.DAYS.between(start, end) > 366) throw new BizException("单次对账周期不能超过一年");
    }
    private static void requireText(String text, int max, String label) {
        if (text == null || text.isBlank() || text.length() > max) throw new BizException(label + "必填，最多" + max + "字");
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new BizException("对账数据序列化失败"); }
    }
    private String fingerprint(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialize(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new BizException("对账数据摘要生成失败"); }
    }
}
