package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktWarehouseOperationsControllerTest {
    final MktOperationWarehouseMapper sites = mock(MktOperationWarehouseMapper.class);
    final MktCustomerOperationWarehouseMapper links = mock(MktCustomerOperationWarehouseMapper.class);
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final CustomerMapper customers = mock(CustomerMapper.class);
    final SysAuditMapper audits = mock(SysAuditMapper.class);
    final MktWarehouseOperationsController controller = new MktWarehouseOperationsController(sites, links, warehouses, customers, audits, new ObjectMapper());

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(MktOperationWarehouse.class, MktCustomerOperationWarehouse.class, Customer.class))
            TableInfoHelper.initTableInfo(assistant, entity);
    }

    @Test void manuallyCreatesSiteWithoutAnyExternalAccountAndNormalizesFields() {
        validWarehouse(); when(audits.insert(any(SysAudit.class))).thenReturn(1);
        when(sites.insert(any(MktOperationWarehouse.class))).thenAnswer(call -> {
            MktOperationWarehouse site = call.getArgument(0); site.setId(12L);
            assertThat(site.getName()).isEqualTo("新华二部"); assertThat(site.getAddress()).isEqualTo("广州");
            assertThat(site.getWarehouseId()).isEqualTo(7L); assertThat(site.getTenantId()).isEqualTo(1L);
            return 1;
        });
        assertThat(controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("  新华二部  ", " 广州 ")).getData()).isEqualTo("12");
        verify(audits).insert(argThat((SysAudit a) -> "operation_warehouse.create".equals(a.getAction()) && a.getAfterJson().contains("新华二部")));
        verifyNoInteractions(links, customers);
    }

    @Test void rejectsBlankOversizeAndDuplicateSiteNamesWithActionableConflict() {
        assertThatThrownBy(() -> controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("  ", ""))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("仓库", "a".repeat(301)))).isInstanceOf(BizException.class);
        verifyNoInteractions(warehouses, sites, audits);
        validWarehouse(); when(sites.insert(any(MktOperationWarehouse.class))).thenThrow(new DuplicateKeyException("unique"));
        assertThatThrownBy(() -> controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("已有仓库", "")))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(409)).hasMessageContaining("已有仓库");
        verifyNoInteractions(audits);
    }

    @Test void createsIndependentCustomerRelationshipUsingOnlyLocalIdsAndLocksParentFirst() {
        validSubjects(); when(audits.insert(any(SysAudit.class))).thenReturn(1);
        when(links.insert(any(MktCustomerOperationWarehouse.class))).thenAnswer(call -> {
            MktCustomerOperationWarehouse link = call.getArgument(0); link.setId(40L);
            assertThat(link.getCustomerId()).isEqualTo(22L); assertThat(link.getOperationWarehouseId()).isEqualTo(12L);
            return 1;
        });
        controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L));
        var order = inOrder(warehouses, customers, sites, links, audits);
        order.verify(warehouses).selectForUpdate(7L); order.verify(customers).selectForUpdate(22L);
        order.verify(sites).selectForUpdate(12L, 1L); order.verify(links).insert(any(MktCustomerOperationWarehouse.class));
        order.verify(audits).insert(any(SysAudit.class));
    }

    @Test void rejectsSiteFromAnotherFranchiseAndOtherTenantOrProjectCustomer() {
        validSubjects(); MktOperationWarehouse foreignSite = site(); foreignSite.setWarehouseId(8L);
        when(sites.selectForUpdate(12L, 1L)).thenReturn(foreignSite);
        assertThatThrownBy(() -> controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L))).hasMessageContaining("不属于当前加盟云仓");
        Customer foreignCustomer = customer(); foreignCustomer.setTenantId(2L); when(customers.selectForUpdate(22L)).thenReturn(foreignCustomer);
        assertThatThrownBy(() -> controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L))).hasMessageContaining("客户不存在");
        foreignCustomer.setTenantId(1L); foreignCustomer.setProjectId(200L);
        assertThatThrownBy(() -> controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L))).hasMessageContaining("其他园区");
        verifyNoInteractions(links, audits);
    }

    @Test void rejectsDeletedOrExitedParentsAndInactiveSite() {
        assertThatThrownBy(() -> controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("仓库", ""))).hasMessageContaining("不存在或已删除");
        MktWarehouse warehouse = validWarehouse(); warehouse.setJoinStatus(7);
        assertThatThrownBy(() -> controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("仓库", ""))).hasMessageContaining("已退出");
        validSubjects(); MktOperationWarehouse inactiveSite = site(); inactiveSite.setStatus(0); when(sites.selectForUpdate(12L, 1L)).thenReturn(inactiveSite);
        assertThatThrownBy(() -> controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L))).hasMessageContaining("作业仓库已停用");
        verifyNoInteractions(links, audits);
    }

    @Test void duplicateRelationshipCannotSilentlyReassignOrReactivateAnotherRecord() {
        validSubjects(); when(links.insert(any(MktCustomerOperationWarehouse.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> controller.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L)))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(409)).hasMessageContaining("重新启用");
        verify(links, never()).update(isNull(), any()); verifyNoInteractions(audits);
    }

    @Test void canDeactivateHistoryWithoutExistingParentButScopesAndComparesStatus() {
        MktCustomerOperationWarehouse link = link(1); when(links.selectForUpdate(40L, 7L, 1L)).thenReturn(link);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
        when(links.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<MktCustomerOperationWarehouse> update = call.getArgument(1);
            assertThat(update.getSqlSegment()).contains("tenant_id", "warehouse_id", "id", "status");
            assertThat(update.getParamNameValuePairs().values()).contains(1L, 7L, 40L, 1, 0);
            return 1;
        });
        controller.status(7L, 40L, new MktWarehouseOperationsController.StatusInput(0));
        verifyNoInteractions(warehouses, customers, sites);
        verify(audits).insert(argThat((SysAudit a) -> a.getBeforeJson().contains("\"status\":1") && a.getAfterJson().contains("\"status\":0")));
    }

    @Test void reactivationRequiresAllLiveSubjectsAndCannotCrossWarehouseRoute() {
        validSubjects(); when(links.selectOne(any())).thenReturn(link(0)); when(links.selectForUpdate(40L, 7L, 1L)).thenReturn(link(0));
        when(links.update(isNull(), any())).thenReturn(1); when(audits.insert(any(SysAudit.class))).thenReturn(1);
        controller.status(7L, 40L, new MktWarehouseOperationsController.StatusInput(1));
        verify(customers).selectForUpdate(22L); verify(sites).selectForUpdate(12L, 1L);
        assertThatThrownBy(() -> controller.status(8L, 40L, new MktWarehouseOperationsController.StatusInput(0))).hasMessageContaining("不存在");
    }

    @Test void concurrentStateUpdateFailureDoesNotWriteSuccessAudit() {
        when(links.selectForUpdate(40L, 7L, 1L)).thenReturn(link(1)); when(links.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> controller.status(7L, 40L, new MktWarehouseOperationsController.StatusInput(0)))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(409));
        verifyNoInteractions(audits);
    }

    @Test void auditFailureRollsBackTransactionAfterSiteInsert() {
        validWarehouse(); when(sites.insert(any(MktOperationWarehouse.class))).thenAnswer(call -> { call.<MktOperationWarehouse>getArgument(0).setId(12L); return 1; });
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class); var tx = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(tx);
        ProxyFactory proxy = new ProxyFactory(controller); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        MktWarehouseOperationsController transactional = (MktWarehouseOperationsController) proxy.getProxy();
        assertThatThrownBy(() -> transactional.createSite(7L, new MktWarehouseOperationsController.SiteInput("新华二部", ""))).hasMessageContaining("审计保存失败");
        verify(manager).rollback(tx); verify(manager, never()).commit(any());
    }

    @Test void sitesFilterByTenantFranchiseAndStatusBeforePagingAndValidateBounds() {
        assertThatThrownBy(() -> controller.sites(7L, null, 1, 0, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.links(7L, 1, 101)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.sites(7L, null, 2, 1, 20)).isInstanceOf(BizException.class);
        verifyNoInteractions(sites, links);
        when(sites.selectPage(any(), any())).thenAnswer(call -> {
            LambdaQueryWrapper<MktOperationWarehouse> query = call.getArgument(1);
            assertThat(query.getSqlSegment()).contains("tenant_id", "warehouse_id", "status", "name");
            assertThat(query.getParamNameValuePairs().values()).contains(1L, 7L, 1, "%新华%");
            Page<MktOperationWarehouse> page = call.getArgument(0); page.setTotal(1); page.setRecords(List.of(site())); return page;
        });
        var result = controller.sites(7L, " 新华 ", 1, 1, 20).getData();
        assertThat(result.getTotal()).isEqualTo(1); assertThat(result.getRecords().get(0).name()).isEqualTo("新华二部");
    }

    @Test void relationshipHistoryRemainsReadableWithDeletedCustomer() {
        when(links.selectPage(any(), any())).thenAnswer(call -> {
            Page<MktCustomerOperationWarehouse> page = call.getArgument(0); page.setTotal(1); page.setRecords(List.of(link(0))); return page;
        });
        when(customers.selectList(any())).thenReturn(List.of()); when(sites.selectList(any())).thenReturn(List.of(site()));
        var row = controller.links(7L, 1, 20).getData().getRecords().get(0);
        assertThat(row.customerName()).isEqualTo("客户已删除"); assertThat(row.operationWarehouseName()).isEqualTo("新华二部"); assertThat(row.status()).isZero();
        verifyNoInteractions(warehouses);
    }

    @Test void queryPermissionCannotCreateSitesRelationshipsOrChangeStatus() {
        ProxyFactory proxy = new ProxyFactory(controller); proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        MktWarehouseOperationsController guarded = (MktWarehouseOperationsController) proxy.getProxy();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("viewer", null, List.of(new SimpleGrantedAuthority("crm:marketing:erp:query"))));
        try {
            assertThatThrownBy(() -> guarded.createSite(7L, new MktWarehouseOperationsController.SiteInput("仓库", ""))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> guarded.createLink(7L, new MktWarehouseOperationsController.LinkInput(22L, 12L))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> guarded.status(7L, 40L, new MktWarehouseOperationsController.StatusInput(0))).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(sites, links, warehouses, customers, audits);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void malformedRequestsFailBeanValidationBeforeDatabaseUse() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/crm/marketing/warehouse-operations/7/sites")
                        .contentType("application/json").content("{\"name\":\"  \"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/crm/marketing/warehouse-operations/7/links")
                        .contentType("application/json").content("{\"customerId\":22,\"operationWarehouseId\":-1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(sites, links, warehouses, customers, audits);
    }

    @Test void realJsonKeepsEveryLocalDirectoryAndLinkIdAboveJavascriptSafeIntegerAsString() throws Exception {
        long largeId = 9007199254740993L;
        ObjectMapper mapper = new ObjectMapper();
        var site = mapper.readTree(mapper.writeValueAsString(new MktWarehouseOperationsController.SiteOption(largeId, "仓库", "", 1)));
        assertThat(site.get("id").isTextual()).isTrue(); assertThat(site.get("id").textValue()).isEqualTo("9007199254740993");
        var link = mapper.readTree(mapper.writeValueAsString(new MktWarehouseOperationsController.LinkOption(largeId, largeId + 2,
                "客户", largeId + 4, "仓库", "", 1)));
        assertThat(link.get("id").textValue()).isEqualTo("9007199254740993");
        assertThat(link.get("customerId").textValue()).isEqualTo("9007199254740995");
        assertThat(link.get("operationWarehouseId").textValue()).isEqualTo("9007199254740997");
        var customer = mapper.readTree(mapper.writeValueAsString(new MktYunshanController.CustomerOption(largeId, "超级棒棒猪")));
        var warehouse = mapper.readTree(mapper.writeValueAsString(new MktYunshanController.WarehouseOption(largeId, "WH", "加盟云仓", 5, "manual", 0)));
        assertThat(customer.get("id").textValue()).isEqualTo("9007199254740993");
        assertThat(warehouse.get("id").textValue()).isEqualTo("9007199254740993");
        validWarehouse(); when(audits.insert(any(SysAudit.class))).thenReturn(1);
        when(sites.insert(any(MktOperationWarehouse.class))).thenAnswer(call -> { call.<MktOperationWarehouse>getArgument(0).setId(largeId); return 1; });
        var created = mapper.readTree(mapper.writeValueAsString(controller.createSite(7L, new MktWarehouseOperationsController.SiteInput("仓库", ""))));
        assertThat(created.get("data").isTextual()).isTrue(); assertThat(created.get("data").textValue()).isEqualTo("9007199254740993");
    }

    private MktWarehouse validWarehouse() {
        MktWarehouse w = new MktWarehouse(); w.setId(7L); w.setTenantId(1L); w.setProjectId(100L); w.setJoinStatus(5);
        when(warehouses.selectForUpdate(7L)).thenReturn(w); return w;
    }
    private void validSubjects() { validWarehouse(); when(customers.selectForUpdate(22L)).thenReturn(customer()); when(sites.selectForUpdate(12L, 1L)).thenReturn(site()); }
    private static Customer customer() { Customer c = new Customer(); c.setId(22L); c.setTenantId(1L); c.setProjectId(100L); c.setName("超级棒棒猪"); return c; }
    private static MktOperationWarehouse site() { MktOperationWarehouse s = new MktOperationWarehouse(); s.setId(12L); s.setTenantId(1L); s.setWarehouseId(7L); s.setName("新华二部"); s.setAddress("广州"); s.setStatus(1); return s; }
    private static MktCustomerOperationWarehouse link(int status) { MktCustomerOperationWarehouse l = new MktCustomerOperationWarehouse(); l.setId(40L); l.setTenantId(1L); l.setWarehouseId(7L); l.setCustomerId(22L); l.setOperationWarehouseId(12L); l.setProjectId(100L); l.setStatus(status); return l; }
}
