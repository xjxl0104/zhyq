package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.pur.service.SupplierImportService;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierTenantContactTest {
    private final BizTenantMapper tenants = mock(BizTenantMapper.class);
    private final SupplierController controller = new SupplierController(
            mock(SupplierMapper.class), mock(SupplierContractMapper.class), mock(SupplierImportService.class),
            mock(WorkOrderMapper.class), tenants);

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), BizTenant.class);
    }

    @Test void removingContactClearsFieldsWithoutDeletingTenant() {
        when(tenants.update(isNull(), any(Wrapper.class))).thenReturn(1);
        controller.removeTenantContact(9L);
        ArgumentCaptor<Wrapper<BizTenant>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(tenants).update(isNull(), update.capture());
        LambdaUpdateWrapper<BizTenant> wrapper = (LambdaUpdateWrapper<BizTenant>) update.getValue();
        assertThat(wrapper.getSqlSet()).contains("contact=", "phone=");
        assertThat(wrapper.getParamNameValuePairs()).containsEntry("MPGENVAL1", null).containsEntry("MPGENVAL2", null);
        verify(tenants, never()).deleteById(any());
    }

    @Test void missingOrAlreadyRemovedContactIsRejected() {
        when(tenants.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> controller.removeTenantContact(9L))
                .isInstanceOf(BizException.class).hasMessageContaining("不存在或已删除");
    }
}
