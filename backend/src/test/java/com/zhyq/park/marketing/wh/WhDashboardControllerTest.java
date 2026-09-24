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
    @Mock com.zhyq.park.marketing.service.MktCustomerAssignmentService assignments;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktCustomerErpMap.class);
        TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
        TableInfoHelper.initTableInfo(a, com.zhyq.park.crm.entity.Customer.class);
    }

    @Test void dashboardUsesTokenWarehouseAndProject() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse current = new MktWarehouse(); current.setId(11L); current.setProjectId(7L);
        current.setCode("WH-11"); current.setName("A"); current.setJoinStatus(5); current.setErpStatus(2);
        when(warehouses.selectById(11L)).thenReturn(current);
        when(customers.selectCount(any())).thenReturn(0L);
        when(orders.selectCount(any())).thenReturn(0L);
        WhDashboardController controller = new WhDashboardController(warehouses, mappings, customers, orders, assignments);
        Object data = controller.dashboard().getData();
        assertThat(data.toString()).contains("warehouseId=11", "WH-11");
        verify(warehouses, never()).selectById(99L);
        verify(customers,times(2)).selectCount(any());
        verifyNoInteractions(mappings);
        verify(orders,times(2)).selectCount(any());
    }
    @Test void merchantOrdersExcludeBonusAndPlatformFeeFinancialEvents() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse current = new MktWarehouse(); current.setId(11L); current.setProjectId(7L);
        when(warehouses.selectById(11L)).thenReturn(current);
        when(orders.selectPage(any(),any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MktReferralOrder> query = inv.getArgument(1);
            assertThat(query.getSqlSegment()).contains("warehouse_id =", "project_id =", "source_type =");
            assertThat(query.getParamNameValuePairs()).containsValues(11L,7L,2);
            IPage<MktReferralOrder> page = inv.getArgument(0); page.setRecords(List.of()); return page;
        });
        WhDashboardController controller = new WhDashboardController(warehouses,mappings,customers,orders,assignments);
        assertThat(controller.orders(1,20,null).getData().getTotal()).isZero();
        verify(orders).selectPage(any(),any());
    }
}
