package com.zhyq.park.archive;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.archive.controller.ArcDocController;
import com.zhyq.park.archive.entity.ArcDoc;
import com.zhyq.park.archive.entity.ArcFolder;
import com.zhyq.park.archive.mapper.ArcCategoryMapper;
import com.zhyq.park.archive.mapper.ArcDocMapper;
import com.zhyq.park.archive.mapper.ArcFolderMapper;
import com.zhyq.park.archive.service.ArcAnalyzeService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArcDocMoveControllerTest {
    final ArcDocMapper docs = mock(ArcDocMapper.class);
    final ArcFolderMapper folders = mock(ArcFolderMapper.class);
    final ArcDocController controller = new ArcDocController(docs, mock(ArcCategoryMapper.class), folders,
            mock(SysFileMapper.class), mock(FileStorageService.class), mock(ArcAnalyzeService.class));

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArcDoc.class);
    }

    @BeforeEach void setup() {
        when(folders.selectById(20L)).thenReturn(new ArcFolder());
        when(docs.selectCount(any())).thenReturn(2L);
    }

    @Test void movesAllSelectedDocumentsToChosenFolder() {
        controller.move(request(List.of(11L, 12L)));
        ArgumentCaptor<LambdaUpdateWrapper<ArcDoc>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(docs).update(isNull(), update.capture());
        update.getValue().getSqlSegment();
        assertThat(update.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(11L, 12L, 20L);
    }

    @Test void deletedSelectionRejectsEntireMoveInsteadOfSilentlyMovingOnlySome() {
        when(docs.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> controller.move(request(List.of(11L, 12L))))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(409))
                .hasMessageContaining("未移动任何资料");
        verify(docs, never()).update(any(), any());
    }

    @Test void duplicateSelectionIsCountedAndMovedOnce() {
        controller.move(request(List.of(11L, 11L, 12L)));
        ArgumentCaptor<LambdaQueryWrapper<ArcDoc>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(docs).selectCount(query.capture());
        query.getValue().getSqlSegment();
        assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(11L, 12L);
        verify(docs, times(1)).update(isNull(), any());
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0L, -1L})
    void invalidIdsAreRejectedBeforeAnyLookupOrMove(Long invalid) {
        assertThatThrownBy(() -> controller.move(request(Arrays.asList(11L, invalid))))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(docs, folders);
    }

    @Test void legacySingleDocumentCanStillMoveToRoot() {
        when(docs.selectCount(any())).thenReturn(1L);
        var req = new ArcDocController.MoveReq(); req.setId(11L); req.setFolderId(0L);
        controller.move(req);
        verifyNoInteractions(folders);
        ArgumentCaptor<LambdaUpdateWrapper<ArcDoc>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(docs).update(isNull(), update.capture());
        update.getValue().getSqlSegment();
        assertThat(update.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(11L, null);
    }

    private static ArcDocController.MoveReq request(List<Long> ids) {
        var req = new ArcDocController.MoveReq(); req.setIds(ids); req.setFolderId(20L); return req;
    }
}
