package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkOrderCompletionTest {
    final WorkOrderMapper orders = mock(WorkOrderMapper.class);
    final WorkOrderLogMapper logs = mock(WorkOrderLogMapper.class);
    final SysFileMapper files = mock(SysFileMapper.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final WorkOrderService service = new WorkOrderService(orders, logs, mock(ApplicationEventPublisher.class), new WorkOrderPhotoService(files, storage));
    final WorkOrder order = new WorkOrder();
    final SysFile photo = new SysFile();

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
    }
    WorkOrderCompletionTest() {
        order.setId(1L); order.setTenantId(1L); order.setStatus(3);
        photo.setId(7L); photo.setTenantId(1L); photo.setBizType(WorkOrderPhotoService.BIZ_TYPE);
        photo.setCreateBy("repair"); photo.setContentType("image/jpeg"); photo.setStorePath("repair.jpg");
        when(orders.selectById(1L)).thenReturn(order);
        when(files.selectById(7L)).thenReturn(photo);
        when(files.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(orders.update(any(WorkOrder.class), any(Wrapper.class))).thenReturn(1);
        when(storage.resolveExisting("repair.jpg")).thenReturn(Path.of("repair.jpg"));
    }
    void finish(List<Long> ids) { service.finish(1L, "repair", "漏水已修复", null, ids); }
    @Test void missingPhotosCannotAdvanceOrCreateLogIncludingLegacyCalls() {
        assertThatThrownBy(() -> finish(null)).isInstanceOf(BizException.class).hasMessageContaining("至少");
        assertThatThrownBy(() -> finish(List.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.finish(1L, "repair", "完成")).isInstanceOf(BizException.class);
        verify(orders, never()).update(any(), any(Wrapper.class));
        verifyNoInteractions(logs);
    }
    @Test void validProcessingPhotoIsBoundAndStatusConditionallyAdvances() {
        finish(List.of(7L));
        var change = ArgumentCaptor.forClass(WorkOrder.class);
        var condition = ArgumentCaptor.forClass(Wrapper.class);
        verify(orders).update(change.capture(), condition.capture());
        assertThat(change.getValue().getStatus()).isEqualTo(4);
        assertThat(change.getValue().getFinishTime()).isNotNull();
        assertThat(condition.getValue().getSqlSegment()).contains("id =", "status =");
        verify(files).update(isNull(), argThat(w -> w.getSqlSegment().contains("biz_id IS NULL")));
        var log = ArgumentCaptor.forClass(WorkOrderLog.class);
        verify(logs).insert(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("处理");
    }
    @Test void rejectsReportAttachmentsOtherOrdersOtherUsersAndNonImages() {
        photo.setBizType("work_order");
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        photo.setBizType(WorkOrderPhotoService.BIZ_TYPE); photo.setBizId(99L);
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        photo.setBizId(null); photo.setCreateBy("someone-else");
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        photo.setCreateBy("repair"); photo.setTenantId(2L);
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        photo.setTenantId(1L); photo.setContentType("application/pdf");
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        verifyNoInteractions(logs);
        verify(orders, never()).update(any(), any(Wrapper.class));
    }
    @Test void deletedOrMissingPhysicalPhotosCannotAdvance() {
        when(files.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        when(files.selectById(7L)).thenReturn(photo);
        when(storage.resolveExisting("repair.jpg")).thenThrow(new BizException("不存在"));
        assertThatThrownBy(() -> finish(List.of(7L))).isInstanceOf(BizException.class);
        verifyNoInteractions(logs);
    }
    @Test void concurrentDeletionOrStatusChangeRaisesRollbackInsteadOfLoggingSuccess() {
        when(files.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> finish(List.of(7L))).hasMessageContaining("照片已被删除");
        verify(orders, never()).update(any(), any(Wrapper.class));
        when(files.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(orders.update(any(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> finish(List.of(7L))).hasMessageContaining("状态已变化");
        verifyNoInteractions(logs);
    }
    @Test void completedOrderCannotResubmit() {
        order.setStatus(4);
        assertThatThrownBy(() -> finish(List.of(7L))).hasMessageContaining("仅处理中的");
        verify(files, never()).update(any(), any(Wrapper.class));
        verifyNoInteractions(logs);
    }
    @Test void verificationBindsOnlyItsOwnPhotosBeforeCompletingOrder() {
        order.setStatus(4);
        photo.setBizType(WorkOrderPhotoService.VERIFY_BIZ_TYPE);
        service.verify(1L, "repair", 5, List.of(7L));
        verify(files).update(isNull(), argThat(w -> w.getSqlSegment().contains("biz_id IS NULL")));
        verify(orders).update(argThat(change -> change.getStatus() == 5 && change.getScore() == 5),
                argThat(w -> w.getSqlSegment().contains("status =")));
        verify(logs).insert(argThat((WorkOrderLog log) -> "验收".equals(log.getAction())));
    }
    @Test void verificationRejectsProcessingPhotoAndKeepsOrderPending() {
        order.setStatus(4);
        assertThatThrownBy(() -> service.verify(1L, "repair", 5, List.of(7L)))
                .hasMessageContaining("验收照片不可用");
        verify(orders, never()).update(any(), any(Wrapper.class));
        verifyNoInteractions(logs);
    }
    @Test void uploadValidatesActualImageBytesInsteadOfTrustingFilenameOrMime() throws Exception {
        byte[] fake = "not an image".getBytes();
        assertThatThrownBy(() -> WorkOrderPhotoService.validateUpload(new MockMultipartFile("file", "fake.jpg", "image/jpeg", fake)))
                .isInstanceOf(BizException.class);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertThat(WorkOrderPhotoService.validateUpload(new MockMultipartFile("file", "现场.png", "application/octet-stream", bytes.toByteArray())))
                .isEqualTo("image/png");
        assertThatThrownBy(() -> WorkOrderPhotoService.validateUpload(new MockMultipartFile("file", "fake.jpg", "image/jpeg", bytes.toByteArray())))
                .hasMessageContaining("格式不一致");
    }
}
