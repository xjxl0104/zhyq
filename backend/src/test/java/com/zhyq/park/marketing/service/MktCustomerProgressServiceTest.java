package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktCustomerProgressServiceTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final MktCustomerProgressService service = new MktCustomerProgressService(customers, jdbc);
    Customer customer;

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Customer.class);
    }
    @BeforeEach void fixture() {
        customer = new Customer(); customer.setId(7L); customer.setTenantId(1L); customer.setVersion(3);
        customer.setStatus(1); customer.setWarehouseAssignmentStatus(2); customer.setProgressPercentage(60);
        when(customers.selectForUpdate(7L)).thenReturn(customer);
        when(customers.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }
    @Test void unsignedCustomerCannotBeMarkedCompleteManually() {
        assertThatThrownBy(() -> service.publish(7L, "尚未签约", 100, "warehouse"))
                .isInstanceOf(BizException.class).hasMessageContaining("0–99");
        verify(customers, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(jdbc);
    }
    @Test void eachPublicationAppendsHistoryInsteadOfOverwritingPreviousUpdates() {
        service.publish(7L, "已确认打包方案", 70, "warehouse");
        service.publish(7L, "报价沟通完成", 80, "warehouse");
        verify(jdbc).update(startsWith("INSERT INTO crm_customer_progress_history"), eq(7L), eq(1L), eq(70),
                eq("已确认打包方案"), eq("following"), eq("warehouse"), any(LocalDateTime.class));
        verify(jdbc).update(startsWith("INSERT INTO crm_customer_progress_history"), eq(7L), eq(1L), eq(80),
                eq("报价沟通完成"), eq("following"), eq("warehouse"), any(LocalDateTime.class));
        verify(jdbc, never()).update(startsWith("UPDATE crm_customer_progress_history"), any(Object[].class));
    }
    @Test void oldClientCanPublishSummaryWithoutResettingPercentage() {
        service.publish(7L, "继续跟进中", null, "admin");
        verify(jdbc).update(anyString(), eq(7L), eq(1L), eq(60), eq("继续跟进中"), eq("following"), eq("admin"), any(LocalDateTime.class));
    }
    @Test void signedStatusAlwaysShowsCompletedOnManualUpdates() {
        customer.setStatus(2); customer.setProgressPercentage(100);
        service.publish(7L, "合同履约中", 50, "warehouse");
        verify(jdbc).update(anyString(), eq(7L), eq(1L), eq(100), eq("合同履约中"), eq("signed"), eq("warehouse"), any(LocalDateTime.class));
    }
    @Test void failedSnapshotUpdateCannotAppendMisleadingHistory() {
        when(customers.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> service.publish(7L, "跟进中", 65, "admin")).isInstanceOf(BizException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void historyInsertFailureMustFailTheWholeTransactionalPublication() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.publish(7L, "跟进中", 65, "admin"))
                .isInstanceOf(BizException.class).hasMessageContaining("进度记录保存失败");
    }
    @Test void legacyUnknownPercentageIsNotFabricatedAsZero() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getObject("percentage", Integer.class)).thenReturn(null);
        when(rs.getString("summary")).thenReturn("历史公开进度");
        when(rs.getString("stage")).thenReturn("historical");
        when(rs.getString("source")).thenReturn("legacy");
        when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf("2026-11-01 12:00:00"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(1L)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(rs, 0)));
        var view = service.view(customer);
        var history = (List<Map<String,Object>>) view.get("progressHistory");
        assertThat(history.get(0)).containsEntry("percentage", null).containsEntry("summary", "历史公开进度");
        assertThat(history.get(0)).doesNotContainKeys("operator", "reason", "beforeJson", "phone");
    }
    @Test void percentageParsingRejectsFractionAndOverflow() {
        assertThat(MktCustomerProgressService.requestPercentage(new BigDecimal("70"))).isEqualTo(70);
        assertThatThrownBy(() -> MktCustomerProgressService.requestPercentage(new BigDecimal("70.5"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> MktCustomerProgressService.requestPercentage(Long.MAX_VALUE)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> MktCustomerProgressService.requestPercentage("80")).isInstanceOf(BizException.class);
    }
}
