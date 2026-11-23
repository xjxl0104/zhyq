package com.zhyq.park.file;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.service.FeeNoticePaymentService;
import com.zhyq.park.file.controller.FileController;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileDownloadTicketService;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.marketing.service.MktDocumentAccessService;
import com.zhyq.park.marketing.service.MktDocumentRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeeNoticePaymentUploadTest {
    final MktDocumentAccessService access = mock(MktDocumentAccessService.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final SysFileMapper files = mock(SysFileMapper.class);
    final FeeNoticePaymentService payments = mock(FeeNoticePaymentService.class);
    final FileController controller = new FileController(mock(MktDocumentRetentionService.class), access, storage,
            files, mock(FileDownloadTicketService.class), payments);

    @Test void paymentUploadsUseLockedSpecializedServiceAfterExistingPermissionCheck() {
        var upload = new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1});
        SysFile saved = new SysFile(); saved.setBizId(11L); saved.setBizType(FeeNoticePaymentService.BIZ_TYPE);
        when(payments.upload(upload, 11L)).thenReturn(saved);
        assertThat(controller.upload(upload, FeeNoticePaymentService.BIZ_TYPE, 11L).getData()).isSameAs(saved);
        var order = inOrder(access, payments);
        order.verify(access).write(FeeNoticePaymentService.BIZ_TYPE);
        order.verify(payments).upload(upload, 11L);
        verifyNoInteractions(storage, files);
    }

    @Test void genericAttachCannotBypassNoticeExistenceAndRegenerationLock() {
        var req = new FileController.AttachRequest();
        req.setBizType(FeeNoticePaymentService.BIZ_TYPE); req.setBizId(11L); req.setFileIds(List.of(5L));
        assertThatThrownBy(() -> controller.attach(req)).isInstanceOf(BizException.class).hasMessageContaining("直接上传");
        verifyNoInteractions(files, payments);
    }
}
