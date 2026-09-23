package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktPaymentProofServiceTest {
    final SysFileMapper files=mock(SysFileMapper.class);
    final FileStorageService storage=mock(FileStorageService.class);
    final MktPaymentProofService service=new MktPaymentProofService(files,storage);
    @Test void rejectsArbitraryUrlAndOtherDocumentAttachment(){
        assertThatThrownBy(()->service.require("/uploads/fake.pdf","mkt_withdrawal",1L)).isInstanceOf(BizException.class);
        var file=new SysFile();file.setBizType("mkt_withdrawal");file.setBizId(2L);when(files.selectById(9L)).thenReturn(file);
        assertThatThrownBy(()->service.require("file:9","mkt_withdrawal",1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(storage);
    }
    @Test void resolvesStoredFileBeforeAcceptingProof(){
        var file=new SysFile();file.setBizType("mkt_withdrawal");file.setBizId(1L);file.setStorePath("proof/receipt.pdf");when(files.selectById(9L)).thenReturn(file);
        assertThat(service.require("file:9","mkt_withdrawal",1L)).isEqualTo("file:9");verify(storage).resolveExisting("proof/receipt.pdf");
    }
    @Test void missingPhysicalFileCannotBeUsed(){
        var file=new SysFile();file.setBizType("mkt_withdrawal");file.setBizId(1L);file.setStorePath("missing.pdf");when(files.selectById(9L)).thenReturn(file);
        when(storage.resolveExisting("missing.pdf")).thenThrow(new BizException("文件不存在"));
        assertThatThrownBy(()->service.require("file:9","mkt_withdrawal",1L)).isInstanceOf(BizException.class).hasMessageContaining("文件不存在");
    }
}
