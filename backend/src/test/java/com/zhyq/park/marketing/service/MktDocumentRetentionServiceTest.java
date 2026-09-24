package com.zhyq.park.marketing.service;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MktDocumentRetentionServiceTest {
    @Test void submittedPaymentProofCannotBeDeleted() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("file:7"))).thenReturn(1);
        SysFile f=new SysFile();f.setId(7L);f.setBizType("mkt_withdrawal");
        assertThatThrownBy(()->new MktDocumentRetentionService(jdbc).assertDeletable(f)).isInstanceOf(BizException.class).hasMessageContaining("不能删除");
    }
    @Test void unrelatedFilesKeepExistingDeletionFlow() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);SysFile f=new SysFile();f.setBizType("work_order");
        new MktDocumentRetentionService(jdbc).assertDeletable(f);verifyNoInteractions(jdbc);
    }
}
