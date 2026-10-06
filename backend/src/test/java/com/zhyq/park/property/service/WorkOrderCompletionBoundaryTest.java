package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.controller.FileController;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.property.controller.WorkOrderController;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkOrderCompletionBoundaryTest {
    @Mock WorkOrderMapper orders;
    @Mock BizTenantMapper tenants;
    @Mock WorkOrderService workOrderService;
    @Mock SysFileMapper files;
    @Mock FileStorageService storage;
    @Mock com.zhyq.park.marketing.service.MktDocumentRetentionService retention;
    @Mock com.zhyq.park.marketing.service.MktDocumentAccessService access;
    @InjectMocks WorkOrderController controller;
    @InjectMocks FileController fileController;
    AutoCloseable mocks;
    @BeforeEach void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
        TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("repair", "ignored", List.of()));
    }
    @AfterEach void cleanup() throws Exception { SecurityContextHolder.clearContext(); mocks.close(); }
    @Test void basicEditCannotBypassPhotoRequirementWithDirectStatusChange() {
        WorkOrder existing = new WorkOrder(); existing.setId(1L); existing.setStatus(3);
        when(orders.selectById(1L)).thenReturn(existing);
        WorkOrder patch = new WorkOrder(); patch.setId(1L); patch.setStatus(4);
        assertThatThrownBy(() -> controller.update(patch)).hasMessageContaining("处理完成须上传");
        verify(orders, never()).updateById(any(WorkOrder.class));
    }
    @Test void basicEditDoesNotReplayStaleWorkflowFields() {
        WorkOrder existing = new WorkOrder(); existing.setId(1L); existing.setStatus(3);
        when(orders.selectById(1L)).thenReturn(existing);
        WorkOrder patch = new WorkOrder(); patch.setId(1L); patch.setStatus(3); patch.setTitle("换灯");
        patch.setFinishTime(java.time.LocalDateTime.now()); patch.setResolutionCode("REPAIRED");
        controller.update(patch);
        assertThat(patch.getStatus()).isNull();
        assertThat(patch.getFinishTime()).isNull();
        assertThat(patch.getResolutionCode()).isNull();
    }
    @Test void rejectsTenantFromAnotherParkOrPlatform() {
        BizTenant tenant = new BizTenant();
        tenant.setId(9L); tenant.setTenantId(1L); tenant.setProjectId(8L); tenant.setStatus(1);
        when(tenants.selectById(9L)).thenReturn(tenant);
        WorkOrder order = new WorkOrder(); order.setTitle("漏水"); order.setProjectId(3L); order.setTenantRefId(9L);
        assertThatThrownBy(() -> controller.add(order)).hasMessageContaining("不属于工单所在园区");
        tenant.setProjectId(3L); tenant.setTenantId(2L);
        assertThatThrownBy(() -> controller.add(order)).hasMessageContaining("不存在或已归档");
        verify(orders, never()).insert(any(WorkOrder.class));
    }
    @Test void editingHistoricalOrderDoesNotRejectItsNowArchivedTenant() {
        WorkOrder existing = new WorkOrder(); existing.setId(1L); existing.setStatus(3); existing.setTenantRefId(9L);
        when(orders.selectById(1L)).thenReturn(existing);
        WorkOrder patch = new WorkOrder(); patch.setId(1L); patch.setTenantRefId(9L); patch.setTitle("换灯");
        controller.update(patch);
        verifyNoInteractions(tenants);
        verify(orders).updateById(patch);
    }
    @Test void explicitClearRemovesTenantContactWithoutAffectingPartialUpdates() {
        WorkOrder existing = new WorkOrder(); existing.setId(1L); existing.setStatus(3); existing.setTenantRefId(9L);
        when(orders.selectById(1L)).thenReturn(existing);
        WorkOrder patch = new WorkOrder(); patch.setId(1L); patch.setClearTenantRef(true);
        patch.setTenantContact("旧联系人");
        controller.update(patch);
        assertThat(patch.getTenantContact()).isNull();
        verify(orders).update(eq(patch), any(Wrapper.class));
    }
    @Test void finishUsesAuthenticatedUploaderAndPassesMissingPhotosToMandatoryValidation() {
        controller.finish(1L, new WorkOrderController.FinishRequest("已处理", null, List.of(7L)));
        verify(workOrderService).finish(1L, "repair", "已处理", null, List.of(7L));
        controller.finish(1L, null);
        verify(workOrderService).finish(1L, "repair", null, null, null);
    }
    @Test void genericUploadAndAttachCannotForgeSubmittedProcessingEvidence() {
        assertThatThrownBy(() -> fileController.upload(new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1}), WorkOrderPhotoService.BIZ_TYPE, 1L))
                .isInstanceOf(BizException.class);
        FileController.AttachRequest req = new FileController.AttachRequest();
        req.setBizType(WorkOrderPhotoService.BIZ_TYPE); req.setBizId(1L); req.setFileIds(List.of(7L));
        assertThatThrownBy(() -> fileController.attach(req)).isInstanceOf(BizException.class);
        SysFile file = new SysFile(); file.setId(7L); file.setBizType(WorkOrderPhotoService.BIZ_TYPE);
        when(files.selectById(7L)).thenReturn(file);
        req.setBizType("work_order");
        assertThatThrownBy(() -> fileController.attach(req)).isInstanceOf(BizException.class);
        verifyNoInteractions(storage);
    }
    @Test void deletionIsConditionalSoSubmittedOrConcurrentlySubmittedPhotosRemain() {
        SysFile file = new SysFile(); file.setId(7L); file.setCreateBy("repair"); file.setBizType(WorkOrderPhotoService.BIZ_TYPE); file.setStorePath("x.jpg");
        when(files.selectById(7L)).thenReturn(file);
        when(files.delete(any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> fileController.remove(7L)).hasMessageContaining("不可删除");
        verify(storage, never()).deletePhysical(any());
        when(files.delete(any(Wrapper.class))).thenReturn(1);
        fileController.remove(7L);
        verify(files, times(2)).delete(argThat((Wrapper<SysFile> w) -> w.getSqlSegment().contains("biz_id IS NULL")));
        verify(storage).deletePhysical("x.jpg");
    }
}
