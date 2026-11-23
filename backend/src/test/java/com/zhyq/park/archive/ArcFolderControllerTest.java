package com.zhyq.park.archive;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.archive.controller.ArcFolderController;
import com.zhyq.park.archive.entity.ArcFolder;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.mapper.ArcFolderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Run through Spring's real transaction advice so partial folder writes cannot survive a failure. */
class ArcFolderControllerTest {
    final ArcFolderMapper folders = mock(ArcFolderMapper.class);
    final ArcDocMapper docs = mock(ArcDocMapper.class);
    final RecordingTransactions transactions = new RecordingTransactions();
    final List<Boolean> writesInTransaction = new ArrayList<>();
    final ArcFolder source = folder(10L, 1L, "/1/10/", 2);
    final ArcFolder child = folder(11L, 10L, "/1/10/11/", 3);
    final ArcFolder target = folder(20L, 3L, "/2/3/20/", 3);
    ArcFolderController controller;
    boolean failSecondWrite;
    final DataAccessResourceFailureException unavailable = new DataAccessResourceFailureException("write unavailable");

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArcFolder.class);
    }

    @BeforeEach void setup() {
        ProxyFactory proxy = new ProxyFactory(new ArcFolderController(folders, docs));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        controller = (ArcFolderController) proxy.getProxy();
        when(folders.selectById(10L)).thenReturn(source);
        when(folders.selectById(20L)).thenReturn(target);
        when(folders.selectList(any())).thenReturn(List.of(child));
        when(folders.insert(any(ArcFolder.class))).thenAnswer(call -> {
            call.<ArcFolder>getArgument(0).setId(99L);
            return recordWrite();
        });
        when(folders.update(isNull(), any())).thenAnswer(call -> recordWrite());
    }

    int recordWrite() {
        writesInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (failSecondWrite && writesInTransaction.size() == 2) throw unavailable;
        transactions.persistedWrites++;
        return 1;
    }

    @ParameterizedTest @ValueSource(strings = {"create", "move", "sort"})
    void intermediateFailureRollsBackEveryPriorFolderWrite(String operation) {
        failSecondWrite = true;
        if (operation.equals("sort")) when(folders.selectList(any())).thenReturn(List.of(source, child));
        assertThatThrownBy(() -> {
            if (operation.equals("create")) {
                var req = new ArcFolderController.FolderReq(); req.setName("新文件夹");
                controller.create(req);
            } else if (operation.equals("move")) {
                controller.move(moveRequest(20L));
            } else {
                var req = new ArcFolderController.SortReq(); req.setIds(List.of(10L, 11L));
                controller.sort(req);
            }
        }).isSameAs(unavailable);
        assertThat(writesInTransaction).containsExactly(true, true);
        assertThat(transactions.persistedWrites).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test void successfulMoveUpdatesParentAndEveryDescendantPathAndLevelInOneCommit() {
        controller.move(moveRequest(20L));
        ArgumentCaptor<LambdaUpdateWrapper<ArcFolder>> changes = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(folders, times(2)).update(isNull(), changes.capture());
        assertThat(changes.getAllValues().get(0).getParamNameValuePairs().values()).contains(20L, "/2/3/20/10/", 4);
        assertThat(changes.getAllValues().get(1).getParamNameValuePairs().values()).contains("/2/3/20/10/11/", 5);
        assertThat(writesInTransaction).containsExactly(true, true);
        assertThat(transactions.persistedWrites).isEqualTo(2);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
    }

    @Test void movingIntoDescendantIsRejectedBeforeAnyWrite() {
        when(folders.selectById(11L)).thenReturn(child);
        assertThatThrownBy(() -> controller.move(moveRequest(11L))).hasMessageContaining("子文件夹");
        verify(folders, never()).update(any(), any());
        assertThat(transactions.persistedWrites).isZero();
    }

    private ArcFolderController.MoveReq moveRequest(Long parent) {
        var req = new ArcFolderController.MoveReq(); req.setId(10L); req.setParentId(parent); return req;
    }

    private static ArcFolder folder(Long id, Long parent, String path, int level) {
        ArcFolder folder = new ArcFolder();
        folder.setId(id); folder.setParentId(parent); folder.setPath(path); folder.setLevel(level); folder.setName("目录" + id);
        return folder;
    }

    /** Minimal transactional store used by mapper fakes; commit/rollback is driven by Spring advice. */
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int persistedWrites, initialWrites, commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { initialWrites = persistedWrites; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { persistedWrites = initialWrites; rollbacks++; }
    }
}
