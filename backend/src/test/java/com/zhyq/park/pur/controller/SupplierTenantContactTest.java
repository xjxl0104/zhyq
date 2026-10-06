package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.pur.entity.TenantContact;
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.pur.mapper.TenantContactMapper;
import com.zhyq.park.pur.service.SupplierImportService;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierTenantContactTest {
    private final TenantContactMapper contacts = mock(TenantContactMapper.class);
    private final BizTenantMapper tenants = mock(BizTenantMapper.class);
    private final SupplierController controller = new SupplierController(
            mock(SupplierMapper.class), mock(SupplierContractMapper.class), mock(SupplierImportService.class),
            mock(WorkOrderMapper.class), contacts, tenants);

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TenantContact.class);
    }

    @Test void addsAnUnlistedTenantNameWithoutCreatingATenantDirectoryEntry() {
        controller.addTenantContact(new SupplierController.TenantContactRequest(null, " 新租客 ", null, " 张三 ", "13800000000"));
        ArgumentCaptor<TenantContact> saved = ArgumentCaptor.forClass(TenantContact.class);
        verify(contacts).insert(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("新租客");
        assertThat(saved.getValue().getContact()).isEqualTo("张三");
        assertThat(saved.getValue().getTenantRefId()).isNull();
        verifyNoInteractions(tenants);
    }

    @Test void editingANameDetachesLegacyDirectoryLinkButPreservesContactRecord() {
        TenantContact existing = new TenantContact();
        existing.setId(9L); existing.setTenantId(1L); existing.setName("旧租客"); existing.setTenantRefId(19L);
        when(contacts.selectById(9L)).thenReturn(existing);
        when(contacts.update(isNull(), any(Wrapper.class))).thenReturn(1);
        controller.updateTenantContact(new SupplierController.TenantContactRequest(9L, "新租客", null, "李四", null));
        verify(contacts).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(tenants);
    }

    @Test void deletingContactDoesNotDeleteTenantDirectoryOrHistoricalOrders() {
        when(contacts.delete(any(Wrapper.class))).thenReturn(1);
        controller.removeTenantContact(9L);
        verify(contacts).delete(any(Wrapper.class));
        verifyNoInteractions(tenants);
        when(contacts.delete(any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> controller.removeTenantContact(9L))
                .isInstanceOf(BizException.class).hasMessageContaining("不存在或已删除");
    }
}
