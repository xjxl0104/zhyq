package com.zhyq.park.marketing.controller;

import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktErpCustomerDeletionGuardTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktCustomerErpMapMapper mappings = mock(MktCustomerErpMapMapper.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final MktErpController controller = new MktErpController(mock(MktWarehouseMapper.class), mock(MktWarehouseErpMapper.class),
            mappings, mock(MktErpSyncLogMapper.class), mock(FieldEncryptionService.class), audit, customers);

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
        var order = inOrder(customers, mappings);
        order.verify(customers).selectForUpdate(42L);
        order.verify(mappings).selectOne(any());
        order.verify(mappings).insert(any(MktCustomerErpMap.class));
    }
}
