package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktYunshanDailyVolume;
import com.zhyq.park.marketing.entity.MktYunshanSyncLog;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktYunshanDailyVolumeMapper;
import com.zhyq.park.marketing.mapper.MktYunshanSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/** 完整校验远端快照后一次提交；任何失败都不能留下半批日统计。 */
@Slf4j
@Service
public class YunshanVolumeSyncService {
    public static final int TRIGGER_SCHEDULED = 1;
    public static final int TRIGGER_MANUAL = 2;
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String HAS_STAT_DATA = "HAS_STAT_DATA";
    private static final String NO_STAT_DATA = "NO_STAT_DATA";
    private static final Long TENANT = MyMetaObjectHandler.DEFAULT_TENANT_ID;

    private final YunshanClient client;
    private final MktYunshanVolumeMapMapper mapMapper;
    private final MktYunshanDailyVolumeMapper volumeMapper;
    private final MktYunshanSyncLogMapper logMapper;
    private final TransactionTemplate transaction;
    // 单实例部署。条带锁覆盖远端读取和提交，避免同一映射旧响应覆盖新响应，且不积累锁对象。
    private final ReentrantLock[] locks = new ReentrantLock[64];

    public YunshanVolumeSyncService(YunshanClient client, MktYunshanVolumeMapMapper mapMapper,
                                   MktYunshanDailyVolumeMapper volumeMapper, MktYunshanSyncLogMapper logMapper,
                                   PlatformTransactionManager transactionManager) {
        this.client = client;
        this.mapMapper = mapMapper;
        this.volumeMapper = volumeMapper;
        this.logMapper = logMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        // 手动接口即使有外层事务，失败记录也必须在日数据回滚之后独立保留。
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }

    public static void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) throw new BizException("开始和结束日期不能为空");
        if (start.isAfter(end)) throw new BizException("开始日期不能晚于结束日期");
        if (end.isAfter(LocalDate.now(SHANGHAI))) throw new BizException("结束日期不能晚于上海时区的今天");
        if (ChronoUnit.DAYS.between(start, end) + 1 > 92) throw new BizException("单次查询最多支持92天");
    }

    public MktYunshanSyncLog sync(Long mapId, LocalDate start, LocalDate end, int triggerType) {
        validateRange(start, end);
        if (triggerType != TRIGGER_SCHEDULED && triggerType != TRIGGER_MANUAL) throw new BizException("同步触发类型不正确");
        ReentrantLock lock = lockFor(mapId);
        lock.lock();
        try {
            MktYunshanVolumeMap mapping = requireMapping(mapId);
            try {
                requireActiveSubjects(mapping);
                Snapshot remote = fetch(mapping, start, end);
                return transaction.execute(status -> save(mapping, start, end, triggerType, remote));
            } catch (RuntimeException failure) {
                MktYunshanSyncLog failed = newLog(mapping, start, end, triggerType, 0);
                failed.setErrorCode(failure instanceof YunshanApiException api ? truncate(api.getApiCode(), 64) : "SYNC_FAILED");
                failed.setErrorMsg(failure instanceof BizException ? truncate(failure.getMessage(), 500) : "同步保存失败，请稍后重试");
                try {
                    transaction.executeWithoutResult(status -> {
                        requireWrite(logMapper.insert(failed));
                        markSync(mapId, 0, LocalDateTime.now(SHANGHAI));
                    });
                } catch (RuntimeException logFailure) {
                    failure.addSuppressed(logFailure);
                    log.error("[yunshan] 映射 {} 同步失败且无法记录失败日志", mapId, logFailure);
                }
                if (triggerType == TRIGGER_MANUAL) {
                    if (failure instanceof BizException) throw failure;
                    throw new BizException("云山同步保存失败，请稍后重试");
                }
                log.warn("[yunshan] 映射 {} 同步失败，错误码 {}", mapId, failed.getErrorCode());
                return failed;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 只读：读取本地快照、summary 和完整 daily，缺失记录始终用 null 表示。 */
    public ReconcileResult reconcile(Long mapId, LocalDate start, LocalDate end) {
        validateRange(start, end);
        ReentrantLock lock = lockFor(mapId);
        lock.lock();
        try {
            MktYunshanVolumeMap mapping = requireMapping(mapId);
            YunshanClient.Summary summary = client.summary(mapping.getParentOwnerNo(), mapping.getParentWarehouseNo(), start, end);
            validateSummary(summary);
            Snapshot daily = fetch(mapping, start, end);
            List<MktYunshanDailyVolume> local = localRows(mapId, start, end, false);
            Map<LocalDate, Long> localDays = new TreeMap<>();
            long localTotal = 0;
            for (MktYunshanDailyVolume row : local) {
                if (row.getConsignNum() == null || row.getConsignNum() < 0) throw new BizException("本地票数异常，无法对账");
                localDays.put(row.getStatDate(), row.getConsignNum());
                localTotal = addExact(localTotal, row.getConsignNum());
            }
            List<DayDifference> differences = new ArrayList<>();
            TreeSet<LocalDate> dates = new TreeSet<>(localDays.keySet());
            dates.addAll(daily.days().keySet());
            for (LocalDate date : dates) {
                Long ours = localDays.get(date), theirs = daily.days().get(date);
                if (!Objects.equals(ours, theirs)) {
                    String reason = ours == null ? "LOCAL_MISSING" : theirs == null ? "REMOTE_MISSING" : "VOLUME_MISMATCH";
                    differences.add(new DayDifference(date, ours, theirs, reason));
                }
            }
            boolean summaryMatchesDaily = summary.consignNum() == daily.total()
                    && Objects.equals(summary.dataState(), daily.dataState())
                    && Objects.equals(summary.latestStatDate(), daily.latestStatDate())
                    && Objects.equals(summary.dataAsOf(), daily.dataAsOf());
            boolean matched = HAS_STAT_DATA.equals(daily.dataState()) && summaryMatchesDaily
                    && localTotal == summary.consignNum() && differences.isEmpty();
            return new ReconcileResult(mapId, start, end, localTotal, summary.consignNum(), daily.total(),
                    localTotal - summary.consignNum(), local.size(), daily.days().size(), matched, summaryMatchesDaily,
                    daily.dataState(), daily.latestStatDate(), daily.dataAsOf(), List.copyOf(differences));
        } finally {
            lock.unlock();
        }
    }

    public record ReconcileResult(Long mapId, LocalDate startDate, LocalDate endDate,
                                  @JsonSerialize(using = ToStringSerializer.class) long localTotal,
                                  @JsonSerialize(using = ToStringSerializer.class) long summaryTotal,
                                  @JsonSerialize(using = ToStringSerializer.class) long dailyTotal,
                                  @JsonSerialize(using = ToStringSerializer.class) long difference,
                                  int localDays, int remoteDays, boolean matched, boolean summaryMatchesDaily,
                                  String dataState, LocalDate latestStatDate, LocalDateTime dataAsOf,
                                  List<DayDifference> differences) { }

    public record DayDifference(LocalDate statDate,
                                @JsonSerialize(using = ToStringSerializer.class) Long localConsignNum,
                                @JsonSerialize(using = ToStringSerializer.class) Long remoteConsignNum,
                                String reason) { }

    private Snapshot fetch(MktYunshanVolumeMap mapping, LocalDate start, LocalDate end) {
        long rangeDays = ChronoUnit.DAYS.between(start, end) + 1;
        YunshanClient.DailyPage first = client.daily(mapping.getParentOwnerNo(), mapping.getParentWarehouseNo(), start, end, 1);
        validatePageHeader(first, rangeDays);
        Map<LocalDate, Long> days = new TreeMap<>();
        long total = 0;
        long pageCount = Math.max(1, first.pages());
        for (int page = 1; page <= pageCount; page++) {
            YunshanClient.DailyPage current = page == 1 ? first
                    : client.daily(mapping.getParentOwnerNo(), mapping.getParentWarehouseNo(), start, end, page);
            validatePageHeader(current, rangeDays);
            if (current.total() != first.total() || current.pages() != first.pages()
                    || current.totalConsignNum() != first.totalConsignNum()
                    || !Objects.equals(current.dataState(), first.dataState())
                    || !Objects.equals(current.latestStatDate(), first.latestStatDate())
                    || !Objects.equals(current.dataAsOf(), first.dataAsOf())) {
                throw badResponse("分页期间统计快照发生变化，请重试");
            }
            for (YunshanClient.DayVolume row : current.records()) {
                if (row == null || row.statDate() == null || row.statDate().isBefore(start) || row.statDate().isAfter(end)) {
                    throw badResponse("返回的统计日期超出请求范围");
                }
                if (row.consignNum() < 0) throw badResponse("日票数不能为负数");
                if (days.putIfAbsent(row.statDate(), row.consignNum()) != null) throw badResponse("同一统计日期重复返回");
                total = addExact(total, row.consignNum());
            }
            if (days.size() > first.total()) throw badResponse("返回记录数超过声明总数");
        }
        if (days.size() != first.total()) throw badResponse("分页未返回完整的统计记录");
        if (total != first.totalConsignNum()) throw badResponse("逐日票数合计与云山总票数不一致");
        return new Snapshot(Map.copyOf(days), total, first.dataState(), first.latestStatDate(), first.dataAsOf());
    }

    private void validatePageHeader(YunshanClient.DailyPage page, long rangeDays) {
        if (page == null || page.records() == null) throw badResponse("缺少分页数据");
        validateState(page.dataState());
        if (page.total() < 0 || page.total() > rangeDays || page.totalConsignNum() < 0
                || page.pages() < 0 || page.pages() > Math.max(1, page.total())
                || page.records().size() > YunshanClient.MAX_PAGE_SIZE) throw badResponse("分页条数、页数或票数不合法");
        if (page.total() == 0) {
            if (!page.records().isEmpty() || page.totalConsignNum() != 0 || !NO_STAT_DATA.equals(page.dataState())) {
                throw badResponse("无统计数据的分页状态不一致");
            }
        } else if (page.pages() == 0 || page.records().isEmpty() || !HAS_STAT_DATA.equals(page.dataState())) {
            throw badResponse("有统计数据的分页状态不一致");
        }
        if (NO_STAT_DATA.equals(page.dataState()) && page.dataAsOf() != null) throw badResponse("无统计数据时不应有统计生成时间");
    }

    private void validateSummary(YunshanClient.Summary summary) {
        if (summary == null) throw badResponse("缺少汇总数据");
        validateState(summary.dataState());
        if (summary.consignNum() < 0 || (NO_STAT_DATA.equals(summary.dataState())
                && (summary.consignNum() != 0 || summary.dataAsOf() != null))) throw badResponse("汇总票数与统计状态不一致");
    }

    private MktYunshanSyncLog save(MktYunshanVolumeMap requested, LocalDate start, LocalDate end, int triggerType, Snapshot remote) {
        MktYunshanVolumeMap mapping = mapMapper.selectForUpdate(requested.getId(), TENANT);
        if (mapping == null || !Objects.equals(requested.getCustomerId(), mapping.getCustomerId())
                || !Objects.equals(requested.getWarehouseId(), mapping.getWarehouseId())
                || !Objects.equals(requested.getParentOwnerNo(), mapping.getParentOwnerNo())
                || !Objects.equals(requested.getParentWarehouseNo(), mapping.getParentWarehouseNo())) {
            throw new BizException("映射在拉取期间发生变化，请重新拉取");
        }
        requireActiveSubjects(mapping);
        List<MktYunshanDailyVolume> local = localRows(mapping.getId(), start, end, true);
        Map<LocalDate, MktYunshanDailyVolume> existing = new HashMap<>();
        for (MktYunshanDailyVolume row : local) existing.put(row.getStatDate(), row);
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        int changed = 0;
        for (Map.Entry<LocalDate, Long> day : new TreeMap<>(remote.days()).entrySet()) {
            MktYunshanDailyVolume row = existing.remove(day.getKey());
            if (row == null) {
                row = new MktYunshanDailyVolume();
                row.setMapId(mapping.getId()); row.setCustomerId(mapping.getCustomerId()); row.setWarehouseId(mapping.getWarehouseId());
                row.setParentOwnerNo(mapping.getParentOwnerNo()); row.setParentWarehouseNo(mapping.getParentWarehouseNo());
                row.setTenantId(TENANT); row.setProjectId(mapping.getProjectId());
                row.setStatDate(day.getKey()); row.setConsignNum(day.getValue()); row.setFirstConsignNum(day.getValue());
                row.setRevisedCount(0); row.setDataAsOf(remote.dataAsOf()); row.setFetchedAt(now);
                requireWrite(volumeMapper.insert(row));
            } else {
                if (!Objects.equals(row.getConsignNum(), day.getValue())) {
                    row.setConsignNum(day.getValue());
                    row.setRevisedCount(Math.addExact(row.getRevisedCount() == null ? 0 : row.getRevisedCount(), 1));
                    row.setLastRevisedAt(now);
                    changed++;
                }
                row.setDataAsOf(remote.dataAsOf()); row.setFetchedAt(now);
                requireWrite(volumeMapper.updateById(row));
            }
        }
        MktYunshanSyncLog result = newLog(mapping, start, end, triggerType, 1);
        result.setDaysFetched(remote.days().size()); result.setDaysChanged(changed); result.setDaysMissing(existing.size());
        result.setTotalConsign(remote.total()); result.setLatestStatDate(remote.latestStatDate()); result.setDataAsOf(remote.dataAsOf());
        requireWrite(logMapper.insert(result));
        markSync(mapping.getId(), 1, now);
        return result;
    }

    private List<MktYunshanDailyVolume> localRows(Long mapId, LocalDate start, LocalDate end, boolean locked) {
        var query = new LambdaQueryWrapper<MktYunshanDailyVolume>()
                .eq(MktYunshanDailyVolume::getTenantId, TENANT).eq(MktYunshanDailyVolume::getMapId, mapId)
                .between(MktYunshanDailyVolume::getStatDate, start, end).orderByAsc(MktYunshanDailyVolume::getStatDate);
        if (locked) query.last("FOR UPDATE");
        return volumeMapper.selectList(query);
    }

    private MktYunshanVolumeMap requireMapping(Long mapId) {
        MktYunshanVolumeMap mapping = mapMapper.selectOne(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getId, mapId).eq(MktYunshanVolumeMap::getTenantId, TENANT));
        if (mapping == null) throw new BizException("云山映射不存在");
        return mapping;
    }

    private void requireActiveSubjects(MktYunshanVolumeMap mapping) {
        if (!Integer.valueOf(1).equals(mapping.getStatus())) throw new BizException("映射已停用，请先启用后再同步");
        if (mapMapper.countAvailableSubjects(mapping.getId(), TENANT) != 1) throw new BizException("映射关联的客户或云仓已删除、退出或冻结，请停用映射");
    }

    private void markSync(Long mapId, int ok, LocalDateTime now) {
        requireWrite(mapMapper.update(null, new LambdaUpdateWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getId, mapId).eq(MktYunshanVolumeMap::getTenantId, TENANT)
                .set(MktYunshanVolumeMap::getLastSyncAt, now).set(MktYunshanVolumeMap::getLastSyncOk, ok)
                .set(MktYunshanVolumeMap::getUpdateTime, now).set(MktYunshanVolumeMap::getUpdateBy, MyMetaObjectHandler.currentOperator())
                .setSql("version = version + 1")));
    }

    private MktYunshanSyncLog newLog(MktYunshanVolumeMap mapping, LocalDate start, LocalDate end, int triggerType, int ok) {
        MktYunshanSyncLog result = new MktYunshanSyncLog();
        result.setMapId(mapping.getId()); result.setTenantId(TENANT); result.setTriggerType(triggerType);
        result.setStartDate(start); result.setEndDate(end); result.setOk(ok);
        result.setDaysFetched(0); result.setDaysChanged(0); result.setDaysMissing(0);
        return result;
    }

    private ReentrantLock lockFor(Long mapId) {
        if (mapId == null || mapId <= 0) throw new BizException("请选择有效的云山映射");
        return locks[Math.floorMod(mapId.hashCode(), locks.length)];
    }

    private static void validateState(String state) {
        if (!HAS_STAT_DATA.equals(state) && !NO_STAT_DATA.equals(state)) throw badResponse("未知统计数据状态");
    }

    private static long addExact(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException e) { throw badResponse("累计票数超出支持范围"); }
    }

    private static void requireWrite(int count) {
        if (count != 1) throw new BizException("云山数据已被其他操作修改，请重试");
    }

    private static String truncate(String text, int limit) {
        return text == null ? null : text.substring(0, Math.min(text.length(), limit));
    }

    private static YunshanApiException badResponse(String message) { return new YunshanApiException("BAD_RESPONSE", message); }

    private record Snapshot(Map<LocalDate, Long> days, long total, String dataState,
                            LocalDate latestStatDate, LocalDateTime dataAsOf) { }
}
