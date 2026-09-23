package com.zhyq.park.marketing.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktWarehouseFileServiceTest {
    SysFileMapper mapper=mock(SysFileMapper.class);
    FileStorageService storage=mock(FileStorageService.class);
    MktWarehouseFileService service=new MktWarehouseFileService(mapper,storage,new ObjectMapper());
    @Test void rejectsOtherWarehouseFilesEvenForValidFileIds() {
        SysFile f=file(11L); when(mapper.selectById(7L)).thenReturn(f);
        assertThatThrownBy(()->service.requireOwned(7L,12L)).isInstanceOf(BizException.class).hasMessageContaining("不属于");
        verifyNoInteractions(storage);
    }
    @Test void rejectsTemporaryPathAndForeignBusinessFile() {
        assertThatThrownBy(()->service.requireReference("wxfile://tmp/contract.pdf",11L)).isInstanceOf(BizException.class);
        SysFile f=file(11L);f.setBizType("contract");when(mapper.selectById(7L)).thenReturn(f);
        assertThatThrownBy(()->service.requireReference("file:7",11L)).isInstanceOf(BizException.class);
    }
    @Test void attachmentMetadataIsRebuiltFromOwnedServerFile() throws Exception {
        when(mapper.selectById(7L)).thenReturn(file(11L));
        String result=service.validateReferences("[{\"id\":7,\"name\":\"forged\",\"url\":\"https://evil.test\"},{\"id\":7}]",11L);
        var list=new ObjectMapper().readTree(result);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("name").asText()).isEqualTo("contract.pdf");
        assertThat(list.get(0).has("url")).isFalse();
    }
    @Test void rejectsExecutableUploadsBeforeStorage() {
        assertThatThrownBy(()->service.upload(new MockMultipartFile("file","payload.svg","image/svg+xml","x".getBytes()),11L))
            .isInstanceOf(BizException.class);
        verifyNoInteractions(storage,mapper);
    }
    SysFile file(Long wid){SysFile f=new SysFile();f.setId(7L);f.setBizId(wid);f.setBizType("mkt_warehouse");f.setStorePath("2026/09/test.pdf");f.setOriginalName("contract.pdf");return f;}
}
