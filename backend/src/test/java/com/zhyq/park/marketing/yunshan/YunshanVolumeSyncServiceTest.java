package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktYunshanDailyVolume;
import com.zhyq.park.marketing.entity.MktYunshanSyncLog;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktYunshanDailyVolumeMapper;
import com.zhyq.park.marketing.mapper.MktYunshanSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktYunshanVolumeMapMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YunshanVolumeSyncServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = START.plusDays(2);
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 1, 4, 2, 0);
    @Mock YunshanClient client;
    @Mock MktYunshanVolumeMapMapper mapMapper;
    @Mock MktYunshanDailyVolumeMapper volumeMapper;
    @Mock MktYunshanSyncLogMapper logMapper;
    @Mock PlatformTransactionManager transactions;
    YunshanVolumeSyncService service;
    MktYunshanVolumeMap mapping;

    @BeforeAll static void initLambdaCache() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MktYunshanVolumeMap.class);
        TableInfoHelper.initTableInfo(assistant, MktYunshanDailyVolume.class);
        TableInfoHelper.initTableInfo(assistant, MktYunshanSyncLog.class);
    }

    @BeforeEach void setUp() {
        service = new YunshanVolumeSyncService(client, mapMapper, volumeMapper, logMapper, transactions);
        mapping = new MktYunshanVolumeMap(); mapping.setId(1L); mapping.setTenantId(1L); mapping.setStatus(1);
        mapping.setCustomerId(2L); mapping.setWarehouseId(3L); mapping.setParentOwnerNo("000001"); mapping.setParentWarehouseNo("9007199254740993");
        lenient().when(mapMapper.selectOne(any())).thenReturn(mapping);
        lenient().when(mapMapper.selectForUpdate(1L, 1L)).thenReturn(mapping);
        lenient().when(mapMapper.countAvailableSubjects(1L, 1L)).thenReturn(1);
        lenient().when(mapMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(volumeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(volumeMapper.insert(any(MktYunshanDailyVolume.class))).thenReturn(1);
        lenient().when(volumeMapper.updateById(any(MktYunshanDailyVolume.class))).thenReturn(1);
        lenient().when(logMapper.insert(any(MktYunshanSyncLog.class))).thenReturn(1);
        lenient().when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
    }

    @Test void mergesEveryPageBeforeWritingAnyDailyRecord() {
        when(client.daily("000001", "9007199254740993", START, END, 1))
                .thenReturn(page(List.of(day(0, 10)), 3, 2, 60));
        when(client.daily("000001", "9007199254740993", START, END, 2)).thenAnswer(i -> {
            verify(volumeMapper, never()).insert(any(MktYunshanDailyVolume.class));
            return page(List.of(day(1, 20), day(2, 30)), 3, 2, 60);
        });
        var result = sync();
        assertThat(result.getOk()).isEqualTo(1);
        assertThat(result.getDaysFetched()).isEqualTo(3);
        assertThat(result.getTotalConsign()).isEqualTo(60L);
        var written = ArgumentCaptor.forClass(MktYunshanDailyVolume.class);
        verify(volumeMapper, times(3)).insert(written.capture());
        assertThat(written.getAllValues()).extracting(MktYunshanDailyVolume::getConsignNum).containsExactly(10L, 20L, 30L);
        assertThat(written.getAllValues()).allSatisfy(row -> {
            assertThat(row.getFirstConsignNum()).isEqualTo(row.getConsignNum());
            assertThat(row.getParentWarehouseNo()).isEqualTo("9007199254740993");
        });
        verify(transactions).commit(any());
    }

    @Test void totalMismatchWritesOnlyFailureLogAndPropagatesManualFailure() {
        respond(page(List.of(day(0, 10)), 1, 1, 11));
        assertThatThrownBy(this::sync).isInstanceOf(YunshanApiException.class).hasMessageContaining("合计");
        verifyNoInteractions(volumeMapper);
        var failed = ArgumentCaptor.forClass(MktYunshanSyncLog.class);
        verify(logMapper).insert(failed.capture());
        assertThat(failed.getValue().getOk()).isZero();
        assertThat(failed.getValue().getErrorCode()).isEqualTo("BAD_RESPONSE");
        verify(transactions).commit(any());
    }

    @Test void pageFetchFailureCannotLeavePartialDailyData() {
        when(client.daily(anyString(), anyString(), any(), any(), eq(1))).thenReturn(page(List.of(day(0, 10)), 2, 2, 30));
        when(client.daily(anyString(), anyString(), any(), any(), eq(2))).thenThrow(new YunshanApiException("NETWORK", "超时"));
        assertThatThrownBy(this::sync).hasMessageContaining("超时");
        verifyNoInteractions(volumeMapper);
        verify(logMapper).insert(any(MktYunshanSyncLog.class));
    }

    @Test void revisedValuesKeepOriginalValueAndIncrementRevisionOnlyOnce() {
        var old = local(0, 10); old.setFirstConsignNum(8L); old.setRevisedCount(2);
        var unchanged = local(1, 20);
        when(volumeMapper.selectList(any())).thenReturn(List.of(old, unchanged));
        respond(page(List.of(day(0, 11), day(1, 20)), 2, 1, 31));
        var result = sync();
        assertThat(result.getDaysChanged()).isEqualTo(1);
        assertThat(old.getConsignNum()).isEqualTo(11);
        assertThat(old.getFirstConsignNum()).isEqualTo(8);
        assertThat(old.getRevisedCount()).isEqualTo(3);
        assertThat(old.getLastRevisedAt()).isNotNull();
        assertThat(unchanged.getRevisedCount()).isZero();
        assertThat(unchanged.getLastRevisedAt()).isNull();
        verify(volumeMapper, times(2)).updateById(any(MktYunshanDailyVolume.class));
    }

    @Test void missingDayPreservesExistingValueWithoutDeleteOrZeroing() {
        var missing = local(1, 22);
        when(volumeMapper.selectList(any())).thenReturn(List.of(local(0, 10), missing));
        respond(page(List.of(day(0, 10)), 1, 1, 10));
        var result = sync();
        assertThat(result.getDaysMissing()).isEqualTo(1);
        assertThat(missing.getConsignNum()).isEqualTo(22);
        verify(volumeMapper, times(1)).updateById(any(MktYunshanDailyVolume.class));
        verify(volumeMapper, never()).delete(any());
        verify(volumeMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test void noStatisticsDoesNotCreateZeroRowsOrClearHistory() {
        when(volumeMapper.selectList(any())).thenReturn(List.of(local(0, 17)));
        respond(noStats());
        var result = sync();
        assertThat(result.getOk()).isEqualTo(1);
        assertThat(result.getDaysFetched()).isZero();
        assertThat(result.getDaysMissing()).isEqualTo(1);
        assertThat(result.getTotalConsign()).isZero();
        verify(volumeMapper, never()).insert(any(MktYunshanDailyVolume.class));
        verify(volumeMapper, never()).updateById(any(MktYunshanDailyVolume.class));
    }

    @Test void realZeroIsStoredAsAStatisticalRecord() {
        respond(page(List.of(day(0, 0)), 1, 1, 0));
        assertThat(sync().getDaysFetched()).isEqualTo(1);
        var record = ArgumentCaptor.forClass(MktYunshanDailyVolume.class);
        verify(volumeMapper).insert(record.capture());
        assertThat(record.getValue().getConsignNum()).isZero();
    }

    @Test void duplicateDatesAcrossPagesAreRejectedBeforeSave() {
        when(client.daily(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(page(List.of(day(0, 10)), 2, 2, 20));
        assertThatThrownBy(this::sync).hasMessageContaining("重复");
        verifyNoInteractions(volumeMapper);
    }

    @Test void recordsOutsideRequestedRangeAndNegativeVolumesAreRejected() {
        respond(page(List.of(day(-1, 2)), 1, 1, 2));
        assertThatThrownBy(this::sync).hasMessageContaining("范围");
        respond(page(List.of(day(0, -1)), 1, 1, 0));
        assertThatThrownBy(this::sync).hasMessageContaining("负数");
        verifyNoInteractions(volumeMapper);
    }

    @Test void missingPageRecordsAndImpossiblePageCountsAreRejected() {
        respond(page(List.of(day(0, 1)), 2, 1, 1));
        assertThatThrownBy(this::sync).hasMessageContaining("完整");
        respond(page(List.of(day(0, 1)), 1, 0, 1));
        assertThatThrownBy(this::sync).hasMessageContaining("状态");
        respond(page(List.of(day(0, 1)), 1, 1000, 1));
        assertThatThrownBy(this::sync).hasMessageContaining("不合法");
        verifyNoInteractions(volumeMapper);
    }

    @Test void changingMetadataBetweenPagesIsRejected() {
        when(client.daily(anyString(), anyString(), any(), any(), eq(1))).thenReturn(page(List.of(day(0, 10)), 2, 2, 30));
        when(client.daily(anyString(), anyString(), any(), any(), eq(2)))
                .thenReturn(new YunshanClient.DailyPage(List.of(day(1, 20)), 2, 2, 30, "HAS_STAT_DATA", END, AS_OF.plusMinutes(1)));
        assertThatThrownBy(this::sync).hasMessageContaining("快照发生变化");
        verifyNoInteractions(volumeMapper);
    }

    @Test void sumOverflowIsRejectedBeforeSave() {
        respond(page(List.of(day(0, Long.MAX_VALUE), day(1, 1)), 2, 1, Long.MAX_VALUE));
        assertThatThrownBy(this::sync).hasMessageContaining("超出支持范围");
        verifyNoInteractions(volumeMapper);
    }

    @Test void writeFailureRollsBackSnapshotThenCommitsSeparateFailureLog() {
        respond(page(List.of(day(0, 10), day(1, 20)), 2, 1, 30));
        when(volumeMapper.insert(any(MktYunshanDailyVolume.class))).thenReturn(1)
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(this::sync).isInstanceOf(BizException.class).hasMessageContaining("保存失败");
        var order = inOrder(transactions, logMapper);
        order.verify(transactions).getTransaction(argThat(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(transactions).rollback(any());
        order.verify(transactions).getTransaction(argThat(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(logMapper).insert(argThat((MktYunshanSyncLog entry) -> entry.getOk() == 0));
        order.verify(transactions).commit(any());
    }

    @Test void scheduledFailureIsReturnedAndLoggedWithoutThrowing() {
        respond(page(List.of(day(0, 10)), 1, 1, 11));
        var result = service.sync(1L, START, END, YunshanVolumeSyncService.TRIGGER_SCHEDULED);
        assertThat(result.getOk()).isZero();
        verify(logMapper).insert(result);
    }

    @Test void inactiveOrDeletedSubjectsCannotFetchData() {
        mapping.setStatus(0);
        assertThatThrownBy(this::sync).hasMessageContaining("停用");
        mapping.setStatus(1);
        when(mapMapper.countAvailableSubjects(1L, 1L)).thenReturn(0);
        assertThatThrownBy(this::sync).hasMessageContaining("已删除");
        verifyNoInteractions(client, volumeMapper);
    }

    @Test void mapIsRecheckedAfterRemoteFetchBeforeSaving() {
        when(client.daily(anyString(), anyString(), any(), any(), eq(1))).thenAnswer(i -> {
            mapping.setStatus(0);
            return page(List.of(day(0, 10)), 1, 1, 10);
        });
        assertThatThrownBy(this::sync).hasMessageContaining("停用");
        verifyNoInteractions(volumeMapper);
        verify(transactions).rollback(any());
    }

    @Test void rangeValidationUsesShanghaiTodayAndIncludesBothEndpoints() {
        LocalDate today = LocalDate.now(YunshanVolumeSyncService.SHANGHAI);
        YunshanVolumeSyncService.validateRange(today.minusDays(91), today);
        assertThatThrownBy(() -> YunshanVolumeSyncService.validateRange(today.minusDays(92), today)).hasMessageContaining("92天");
        assertThatThrownBy(() -> YunshanVolumeSyncService.validateRange(today, today.plusDays(1))).hasMessageContaining("上海");
        assertThatThrownBy(() -> YunshanVolumeSyncService.validateRange(today, today.minusDays(1))).hasMessageContaining("开始日期");
        assertThatThrownBy(() -> YunshanVolumeSyncService.validateRange(null, today)).hasMessageContaining("不能为空");
        verifyNoInteractions(client, transactions);
    }

    @Test void reconciliationComparesSummaryAndDailyWithoutWriting() {
        when(volumeMapper.selectList(any())).thenReturn(List.of(local(0, 9), local(2, 0)));
        when(client.summary(anyString(), anyString(), any(), any())).thenReturn(summary(30));
        respond(page(List.of(day(0, 10), day(1, 20)), 2, 1, 30));
        var result = service.reconcile(1L, START, END);
        assertThat(result.localTotal()).isEqualTo(9);
        assertThat(result.summaryTotal()).isEqualTo(30);
        assertThat(result.difference()).isEqualTo(-21);
        assertThat(result.summaryMatchesDaily()).isTrue();
        assertThat(result.matched()).isFalse();
        assertThat(result.differences()).extracting(YunshanVolumeSyncService.DayDifference::reason)
                .containsExactly("VOLUME_MISMATCH", "LOCAL_MISSING", "REMOTE_MISSING");
        assertThat(result.differences().get(1).localConsignNum()).isNull();
        assertThat(result.differences().get(2).remoteConsignNum()).isNull();
        verifyNoInteractions(logMapper, transactions);
        verify(mapMapper, never()).update(isNull(), any());
        verify(volumeMapper, never()).insert(any(MktYunshanDailyVolume.class));
        verify(volumeMapper, never()).updateById(any(MktYunshanDailyVolume.class));
    }

    @Test void absentStatisticsNeverReconcilesAsTrueZero() {
        when(client.summary(anyString(), anyString(), any(), any())).thenReturn(new YunshanClient.Summary(0, "NO_STAT_DATA", START.minusDays(1), null));
        respond(noStats());
        assertThat(service.reconcile(1L, START, END).matched()).isFalse();
        when(client.summary(anyString(), anyString(), any(), any())).thenReturn(summary(0));
        when(volumeMapper.selectList(any())).thenReturn(List.of(local(0, 0)));
        respond(page(List.of(day(0, 0)), 1, 1, 0));
        assertThat(service.reconcile(1L, START, END).matched()).isTrue();
    }

    @Test void summaryMismatchIsVisibleEvenWhenLocalDailyRowsMatch() {
        when(client.summary(anyString(), anyString(), any(), any())).thenReturn(summary(11));
        when(volumeMapper.selectList(any())).thenReturn(List.of(local(0, 10)));
        respond(page(List.of(day(0, 10)), 1, 1, 10));
        var result = service.reconcile(1L, START, END);
        assertThat(result.summaryMatchesDaily()).isFalse();
        assertThat(result.matched()).isFalse();
        assertThat(result.differences()).isEmpty();
    }

    @Test void concurrentRequestsForSameMappingDoNotFetchOverlappingSnapshots() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(client.daily(anyString(), anyString(), any(), any(), eq(1))).thenAnswer(i -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
            }
            return page(List.of(day(0, 10)), 1, 1, 10);
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(this::sync);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> { secondStarted.countDown(); return sync(); });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls.get()).isEqualTo(1);
            assertThat(second.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getOk()).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS).getOk()).isEqualTo(1);
            assertThat(calls.get()).isEqualTo(2);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void volumeNumbersSerializeAsStringsWithoutJavascriptPrecisionLoss() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var row = local(0, 9007199254740993L);
        var json = mapper.readTree(mapper.writeValueAsString(row));
        assertThat(json.path("consignNum").isTextual()).isTrue();
        assertThat(json.path("consignNum").asText()).isEqualTo("9007199254740993");
        var diff = new YunshanVolumeSyncService.DayDifference(START, 9007199254740993L, null, "REMOTE_MISSING");
        json = mapper.readTree(mapper.writeValueAsString(diff));
        assertThat(json.path("localConsignNum").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("remoteConsignNum").isNull()).isTrue();
    }

    private MktYunshanSyncLog sync() { return service.sync(1L, START, END, YunshanVolumeSyncService.TRIGGER_MANUAL); }
    private void respond(YunshanClient.DailyPage page) { when(client.daily(anyString(), anyString(), any(), any(), eq(1))).thenReturn(page); }
    private static YunshanClient.DayVolume day(int offset, long count) { return new YunshanClient.DayVolume(START.plusDays(offset), count); }
    private static YunshanClient.DailyPage page(List<YunshanClient.DayVolume> records, long total, long pages, long sum) {
        return new YunshanClient.DailyPage(records, total, pages, sum, "HAS_STAT_DATA", END, AS_OF);
    }
    private static YunshanClient.DailyPage noStats() {
        return new YunshanClient.DailyPage(List.of(), 0, 0, 0, "NO_STAT_DATA", START.minusDays(1), null);
    }
    private static YunshanClient.Summary summary(long sum) { return new YunshanClient.Summary(sum, "HAS_STAT_DATA", END, AS_OF); }
    private static MktYunshanDailyVolume local(int offset, long count) {
        var row = new MktYunshanDailyVolume(); row.setId(10L + offset); row.setMapId(1L); row.setTenantId(1L);
        row.setStatDate(START.plusDays(offset)); row.setConsignNum(count); row.setFirstConsignNum(count); row.setRevisedCount(0); row.setVersion(1);
        return row;
    }
}
