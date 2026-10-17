package com.zhyq.park.marketing.controller;

import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.open.ErpConnectionService;
import com.zhyq.park.marketing.open.ErpExceptionService;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktErpCustomerDeletionGuardTest {
    @BeforeAll static void tables() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Customer.class);
        TableInfoHelper.initTableInfo(assistant, MktCustomerErpMap.class);
    }
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktCustomerErpMapMapper mappings = mock(MktCustomerErpMapMapper.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final ErpExceptionService exceptions = mock(ErpExceptionService.class);
    final MktErpController controller = new MktErpController(warehouses, mock(MktWarehouseErpMapper.class),
            mappings, mock(MktErpSyncLogMapper.class), mock(FieldEncryptionService.class), audit, customers,
            mock(ErpConnectionService.class), exceptions);

    @BeforeEach void warehouse() {
        MktWarehouse w = new MktWarehouse(); w.setId(9L); w.setJoinStatus(5);
        when(warehouses.selectForUpdate(9L)).thenReturn(w);
    }

    @Test void missingCustomerCannotGainNewErpMappingAfterDeletion() {
        assertThatThrownBy(() -> controller.saveMapping(9L, Map.of("customerId", 42L, "customerCode", "TEST")))
                .hasMessageContaining("不存在或已删除");
        verify(customers).selectForUpdate(42L);
        verifyNoInteractions(mappings, audit);
    }

    @Test void mappingWriterTakesSameCustomerLockBeforeAddingDependency() {
        Customer customer = new Customer(); customer.setId(42L);
        when(customers.selectForUpdate(42L)).thenReturn(customer);
        controller.saveMapping(9L, Map.of("customerId", 42L, "customerCode", "TEST"));
        var order = inOrder(warehouses, customers, mappings, exceptions);
        order.verify(warehouses).selectForUpdate(9L);
        order.verify(customers).selectForUpdate(42L);
        order.verify(mappings).selectOne(any());
        order.verify(mappings).insert(any(MktCustomerErpMap.class));
        order.verify(exceptions).enqueueForMapping(9L, "TEST");
    }

    @Test void missingCodeDoesNotBecomeLiteralNullMapping() {
        Customer customer = new Customer(); customer.setId(42L);
        when(customers.selectForUpdate(42L)).thenReturn(customer);
        assertThatThrownBy(() -> controller.saveMapping(9L, Map.of("customerId", 42L))).hasMessageContaining("货主编码");
        verifyNoInteractions(mappings, exceptions);
    }

    @Test void mappingListUsesCurrentCustomerNamesByIdAndPreservesMissingCustomers() {
        MktCustomerErpMap first = new MktCustomerErpMap(); first.setCustomerId(42L); first.setCustomerCode("OWNER-A");
        MktCustomerErpMap second = new MktCustomerErpMap(); second.setCustomerId(43L); second.setCustomerCode("OWNER-B");
        MktCustomerErpMap missing = new MktCustomerErpMap(); missing.setCustomerId(44L); missing.setCustomerCode("OWNER-C");
        when(mappings.selectList(any())).thenReturn(List.of(first, second, missing));
        Customer a = new Customer(); a.setId(42L); a.setName("同名客户");
        Customer b = new Customer(); b.setId(43L); b.setName("同名客户");
        when(customers.selectList(any())).thenReturn(List.of(b, a));
        var result = controller.mappings(9L).getData();
        assertThat(result).extracting(MktCustomerErpMap::getCustomerId).containsExactly(42L, 43L, 44L);
        assertThat(result).extracting(MktCustomerErpMap::getCustomerName).containsExactly("同名客户", "同名客户", null);
        assertThat(result).extracting(MktCustomerErpMap::getCustomerCode).containsExactly("OWNER-A", "OWNER-B", "OWNER-C");
        verify(customers, times(1)).selectList(any());
        verifyNoInteractions(audit, exceptions);
    }

    @Test void emptyMappingsDoNotLoadAllCustomers() {
        when(mappings.selectList(any())).thenReturn(List.of());
        assertThat(controller.mappings(9L).getData()).isEmpty();
        verifyNoInteractions(customers);
    }
}
