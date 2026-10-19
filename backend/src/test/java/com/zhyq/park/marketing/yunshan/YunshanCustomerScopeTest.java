package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class YunshanCustomerScopeTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktCustomerOperationWarehouseMapper links = mock(MktCustomerOperationWarehouseMapper.class);
    final MktOperationWarehouseMapper sites = mock(MktOperationWarehouseMapper.class);
    final YunshanCustomerScope scope = new YunshanCustomerScope(customers, links, sites);

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(Customer.class, MktCustomerOperationWarehouse.class, MktOperationWarehouse.class))
            TableInfoHelper.initTableInfo(assistant, type);
    }

    @Test void candidatesRequireTenantCurrentContractAndEffectiveWarehouseAssignment() {
        var query = scope.signedCustomers(77L);
        assertThat(query.getSqlSegment()).contains("sc.status IN (4,5,6)", "sc.deleted=0", "sc.tenant_id=crm_customer.tenant_id",
            "cw.warehouse_id=", "cw.effective_to >", "NOT EXISTS", "sc.start_date <=", "sc.end_date >=");
        assertThat(query.getParamNameValuePairs().values()).contains(77L, LocalDate.now(YunshanVolumeSyncService.SHANGHAI));
        assertThatThrownBy(() -> scope.signedCustomers(null)).hasMessageContaining("当前加盟云仓");
    }

    @Test void unsignedCustomerCannotReadLocalWarehouseAssociations() {
        when(customers.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> scope.sites(77L, 2L)).hasMessageContaining("有效的已签合同");
        verifyNoInteractions(links, sites);
    }

    @Test void warehouseMustBeAnActiveAssociationOfTheSelectedCustomer() {
        when(customers.selectCount(any())).thenReturn(1L);
        var link = new MktCustomerOperationWarehouse(); link.setOperationWarehouseId(99L);
        when(links.selectList(any())).thenReturn(List.of(link));
        var site = new MktOperationWarehouse(); site.setId(99L); site.setName("本地仓库");
        when(sites.selectList(any())).thenReturn(List.of(site));
        assertThat(scope.requireSite(77L, 2L, 99L)).isSameAs(site);
        assertThatThrownBy(() -> scope.requireSite(77L, 2L, 100L)).hasMessageContaining("有效关联");
    }
}
