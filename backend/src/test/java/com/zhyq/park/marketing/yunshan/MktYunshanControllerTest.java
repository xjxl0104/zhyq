package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.controller.MktYunshanController;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktYunshanControllerTest {
    final YunshanClient client = mock(YunshanClient.class);
    final YunshanVolumeSyncService sync = mock(YunshanVolumeSyncService.class);
    final MktYunshanVolumeMapMapper maps = mock(MktYunshanVolumeMapMapper.class);
    final MktYunshanDailyVolumeMapper volumes = mock(MktYunshanDailyVolumeMapper.class);
    final MktYunshanSyncLogMapper logs = mock(MktYunshanSyncLogMapper.class);
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final YunshanCustomerScope scope = mock(YunshanCustomerScope.class);
    final YunshanConnections connections = mock(YunshanConnections.class);
    final MktYunshanController controller = new MktYunshanController(client, sync, maps, volumes, logs, warehouses, customers, audit, scope, connections);

    @org.junit.jupiter.api.BeforeEach void setupScope() {
        var site = new MktOperationWarehouse(); site.setId(99L); site.setName("本地仓库");
        when(scope.requireSite(any(), any(), any())).thenReturn(site);
        when(connections.preview(any())).thenReturn(client);
    }
    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(MktYunshanVolumeMap.class, MktYunshanDailyVolume.class, Customer.class, MktWarehouse.class))
            TableInfoHelper.initTableInfo(assistant, entity);
    }

    @Test void rejectsUnauthorizedCombinationBeforeSavingMapping() {
        when(client.owners(null, "003", 1)).thenReturn(new YunshanClient.EntityPage(List.of(new YunshanClient.Entity("001", "货主")), 1, 1));
        when(client.warehouses(null, "001", 1)).thenReturn(new YunshanClient.EntityPage(List.of(), 0, 0));
        assertThatThrownBy(() -> controller.createMapping(7L, new MktYunshanController.MapInput(2L, "001", "003", true, 99L, new YunshanConnections.Input("SHARED", null, null, null))))
                .hasMessageContaining("未获云山授权");
        verifyNoInteractions(maps, audit, customers, warehouses);
    }

    @Test void unsignedOrUnlinkedCustomerIsRejectedBeforeAnyExternalRequest() {
        doThrow(new com.zhyq.park.common.exception.BizException("没有有效的已签合同"))
            .when(scope).requireSite(7L, 2L, 99L);
        assertThatThrownBy(() -> controller.createMapping(7L, new MktYunshanController.MapInput(
            2L, "001", "003", true, 99L, new YunshanConnections.Input("SHARED", null, null, null))))
            .hasMessageContaining("已签合同");
        verifyNoInteractions(client, maps, audit);
    }

    @Test void directoryPreviewAlsoRequiresContractWithCurrentWarehouse() {
        doThrow(new com.zhyq.park.common.exception.BizException("没有有效的已签合同"))
            .when(scope).requireSigned(7L, 2L);
        assertThatThrownBy(() -> controller.directory(7L, new MktYunshanController.DirectoryInput(
            2L, new YunshanConnections.Input("SHARED", null, null, null), null, 1))).hasMessageContaining("已签合同");
        verifyNoInteractions(client);
    }

    @Test void createsMappingWithAuthoritativeNamesAndFiltersWarehouseByOwnerAcrossPages() {
        when(client.owners(null, "003", 1)).thenReturn(new YunshanClient.EntityPage(List.of(new YunshanClient.Entity("other", "其他")), 2, 2));
        when(client.owners(null, "003", 2)).thenReturn(new YunshanClient.EntityPage(List.of(new YunshanClient.Entity("0009007199254740993", "授权货主")), 2, 2));
        when(client.warehouses(null, "0009007199254740993", 1)).thenReturn(new YunshanClient.EntityPage(List.of(new YunshanClient.Entity("003", "授权仓库")), 1, 1));
        Customer c = new Customer(); c.setId(2L); c.setName(" 授权货主 "); when(customers.selectForUpdate(2L)).thenReturn(c);
        MktWarehouse w = new MktWarehouse(); w.setId(7L); w.setTenantId(1L); w.setJoinStatus(5);
        when(warehouses.selectForUpdate(7L)).thenReturn(w);
        when(maps.insert(any(MktYunshanVolumeMap.class))).thenAnswer(i -> { i.<MktYunshanVolumeMap>getArgument(0).setId(10L); return 1; });
        controller.createMapping(7L, new MktYunshanController.MapInput(2L, "0009007199254740993", "003", true, 99L, new YunshanConnections.Input("SHARED", null, null, null)));
        verify(maps).insert(argThat((MktYunshanVolumeMap m) -> m.getParentOwnerNo().equals("0009007199254740993")
                && m.getParentWarehouseName().equals("授权仓库") && m.getCustomerId().equals(2L) && m.getWarehouseId().equals(7L)));
        verify(audit).log(eq("yunshan.mapping.create"), eq("yunshan_map"), eq(10L), anyString(), isNull(), any());
    }

    @Test void independentCustomerCannotBindAnUnrelatedOwnerEvenWhenConfirmed() {
        when(client.owners(null, "003", 1)).thenReturn(new YunshanClient.EntityPage(
                List.of(new YunshanClient.Entity("001", "广东泽浩棒棒猪科技股份有限公司")), 1, 1));
        when(client.warehouses(null, "001", 1)).thenReturn(new YunshanClient.EntityPage(
                List.of(new YunshanClient.Entity("003", "华南一区-新华一部")), 1, 1));
        Customer customer = new Customer(); customer.setId(2L); customer.setName("超级棒棒猪");
        when(customers.selectForUpdate(2L)).thenReturn(customer);

        assertThatThrownBy(() -> controller.createMapping(7L, new MktYunshanController.MapInput(2L, "001", "003", true, 99L, new YunshanConnections.Input("SHARED", null, null, null))))
                .hasMessageContaining("名称不一致").hasMessageContaining("独立客户");
        verifyNoInteractions(maps, warehouses, audit);
    }

    @Test void explicitConfirmationIsRequiredBeforeCallingTheDirectory() {
        assertThatThrownBy(() -> controller.createMapping(7L, new MktYunshanController.MapInput(2L, "001", "003", null, 99L, new YunshanConnections.Input("SHARED", null, null, null))))
                .hasMessageContaining("确认");
        assertThatThrownBy(() -> controller.createMapping(7L, new MktYunshanController.MapInput(2L, "001", "003", false, 99L, new YunshanConnections.Input("SHARED", null, null, null))))
                .hasMessageContaining("确认");
        verifyNoInteractions(client, maps, customers, warehouses, audit);
    }

    @Test void existingMismatchedMappingCannotBeEnabledEvenIfAlreadyActive() {
        MktYunshanVolumeMap map = new MktYunshanVolumeMap(); map.setId(10L); map.setStatus(1);
        map.setCustomerId(2L); map.setParentOwnerName("广东泽浩棒棒猪科技股份有限公司");
        when(maps.selectById(10L)).thenReturn(map);
        Customer customer = new Customer(); customer.setId(2L); customer.setName("超级棒棒猪");
        when(customers.selectForUpdate(2L)).thenReturn(customer);

        assertThatThrownBy(() -> controller.status(10L, new MktYunshanController.StatusInput(1)))
                .hasMessageContaining("名称不一致");
        verify(maps, never()).update(isNull(), any());
        verifyNoInteractions(warehouses, audit, volumes);
    }

    @Test void mappingListMarksMismatchedIdentitiesWithoutDeletingHistory() {
        MktYunshanVolumeMap map = new MktYunshanVolumeMap(); map.setId(10L); map.setCustomerId(2L);
        map.setParentOwnerName("广东泽浩棒棒猪科技股份有限公司");
        Customer customer = new Customer(); customer.setId(2L); customer.setName("超级棒棒猪");
        when(maps.selectList(any())).thenReturn(List.of(map));
        when(customers.selectBatchIds(any())).thenReturn(List.of(customer));

        var rows = controller.mappings(7L).getData();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("identityMismatch", true).containsEntry("customerName", "超级棒棒猪");
        verifyNoInteractions(volumes, audit);
        verify(maps, never()).update(isNull(), any());
    }

    @Test void volumeTotalsCoverAllPagesPreserveLongPrecisionAndDoNotInventMissingDates() throws Exception {
        when(maps.selectById(10L)).thenReturn(new MktYunshanVolumeMap());
        MktYunshanDailyVolume a = day("2026-10-01", 9007199254740993L), b = day("2026-10-03", 7L);
        when(volumes.selectList(any())).thenReturn(List.of(a, b));
        var result = controller.volume(10L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), 2, 1).getData();
        assertThat(result.records()).containsExactly(b);
        assertThat(result.totalConsignNum()).isEqualTo("9007199254741000");
        assertThat(result.daysMissing()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(2);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(a)).contains("\"consignNum\":\"9007199254740993\"");
        verify(volumes, never()).insert(any(MktYunshanDailyVolume.class));
    }

    @Test void emptyLocalPeriodRemainsNoStatDataAndInvalidPagingDoesNotQueryDatabase() {
        assertThatThrownBy(() -> controller.volume(10L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), 0, 20))
                .hasMessageContaining("页码");
        verifyNoInteractions(maps, volumes);
        when(maps.selectById(10L)).thenReturn(new MktYunshanVolumeMap());
        when(volumes.selectList(any())).thenReturn(List.of());
        var result = controller.volume(10L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), 1, 20).getData();
        assertThat(result.dataState()).isEqualTo("NO_STAT_DATA");
        assertThat(result.daysMissing()).isEqualTo(3);
        assertThat(result.records()).isEmpty();
    }

    @Test void statusTransitionUsesExpectedPreviousStateAndRejectsConcurrentChange() {
        MktYunshanVolumeMap map = new MktYunshanVolumeMap(); map.setId(10L); map.setStatus(1);
        when(maps.selectById(10L)).thenReturn(map);
        when(maps.update(isNull(), any())).thenAnswer(i -> {
            LambdaUpdateWrapper<MktYunshanVolumeMap> update = i.getArgument(1);
            assertThat(update.getSqlSegment()).contains("id", "status");
            assertThat(update.getParamNameValuePairs().values()).contains(10L, 1);
            return 0;
        });
        assertThatThrownBy(() -> controller.status(10L, new MktYunshanController.StatusInput(0))).hasMessageContaining("已变化");
        verifyNoInteractions(audit);
    }

    @Test void queryPermissionAllowsLocalReadButDeniesConfigurationAndSync() {
        ProxyFactory factory = new ProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        MktYunshanController guarded = (MktYunshanController) factory.getProxy();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("viewer", null,
                List.of(new SimpleGrantedAuthority("crm:marketing:erp:query"))));
        try {
            when(client.baseUrl()).thenReturn(""); when(client.maskedAppId()).thenReturn("");
            assertThat(guarded.config().getData()).containsEntry("configured", false);
            assertThatThrownBy(() -> guarded.sync(1L, new MktYunshanController.SyncInput(LocalDate.now(), LocalDate.now())))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> guarded.owners(null, null, 1)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> guarded.status(1L, new MktYunshanController.StatusInput(0))).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(sync, maps, audit);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void malformedMappingAndStatusRequestsFailValidationBeforeAnyRemoteOrDatabaseWork() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/crm/marketing/yunshan/warehouse/7/maps")
                        .contentType("application/json").content("{\"customerId\":2,\"parentOwnerNo\":\" \",\"parentWarehouseNo\":\"003\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/crm/marketing/yunshan/maps/10/status")
                        .contentType("application/json").content("{\"status\":2}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(client, maps, customers, warehouses, audit);
    }

    @Test void missingOrFalseIdentityConfirmationFailsRequestValidation() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        for (String flag : List.of("", ",\"identityConfirmed\":null", ",\"identityConfirmed\":false")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/crm/marketing/yunshan/warehouse/7/maps")
                            .contentType("application/json").content("{\"customerId\":2,\"parentOwnerNo\":\"001\",\"parentWarehouseNo\":\"003\"" + flag + "}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        verifyNoInteractions(client, maps, customers, warehouses, audit);
    }

    private static MktYunshanDailyVolume day(String date, long count) {
        MktYunshanDailyVolume row = new MktYunshanDailyVolume(); row.setStatDate(LocalDate.parse(date));
        row.setConsignNum(count); row.setFirstConsignNum(count); return row;
    }
}
