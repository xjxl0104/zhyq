package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktCustomerErpMapMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.crm.mapper.CustomerMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.zhyq.park.marketing.entity.MktReferralOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhDashboardControllerTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktCustomerErpMapMapper mappings;
    @Mock CustomerMapper customers;
    @Mock MktReferralOrderMapper orders;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktCustomerErpMap.class);
        TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
    }

    @Test void dashboardUsesTokenWarehouseAndProject() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse current = new MktWarehouse(); current.setId(11L); current.setProjectId(7L);
        current.setCode("WH-11"); current.setName("A"); current.setJoinStatus(5); current.setErpStatus(2);
        when(warehouses.selectById(11L)).thenReturn(current);
        when(mappings.selectCount(any())).thenReturn(0L);
        when(orders.selectList(any())).thenReturn(List.of());
        WhDashboardController controller = new WhDashboardController(warehouses, mappings, customers, orders);
        Object data = controller.dashboard().getData();
        assertThat(data.toString()).contains("warehouseId=11", "WH-11");
        verify(warehouses, never()).selectById(99L);
        verify(mappings).selectCount(any());
        verify(orders).selectList(any());
    }
}
