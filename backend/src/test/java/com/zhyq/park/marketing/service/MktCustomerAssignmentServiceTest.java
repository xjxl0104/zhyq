package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Canonical assignment is the authorization boundary for both merchant and contract operations. */
@ExtendWith(MockitoExtension.class)
class MktCustomerAssignmentServiceTest {
    @Mock CustomerMapper customers;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktServiceContractMapper contracts;
    @Mock MktAuditService audit;
    @InjectMocks MktCustomerAssignmentService service;
    Customer customer;
    MktWarehouse warehouse;

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Customer.class);
        TableInfoHelper.initTableInfo(assistant, MktServiceContract.class);
    }

    @BeforeEach
    void fixture() {
        customer = new Customer(); customer.setId(11L); customer.setStatus(1); customer.setServiceType(2);
        customer.setVersion(4); customer.setProjectId(1L); customer.setReferrerId(8L);
        customer.setIntendedWarehouseId(20L); customer.setAssignedWarehouseId(20L); customer.setWarehouseAssignmentStatus(1);
        warehouse = new MktWarehouse(); warehouse.setId(20L); warehouse.setName("测试云仓"); warehouse.setProjectId(1L);
        warehouse.setJoinStatus(5); warehouse.setErpStatus(0); warehouse.setOrderMode("manual");
        lenient().when(customers.selectForUpdate(11L)).thenReturn(customer);
        lenient().when(warehouses.selectById(20L)).thenReturn(warehouse);
    }

    @Test
    void intendedWarehouseDoesNotAuthorizeMerchant() {
        customer.setAssignedWarehouseId(null);
        assertThatThrownBy(() -> service.accept(11L, 20L)).isInstanceOf(BizException.class).hasMessageContaining("无权");
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void wrongMerchantCannotPublishOrReject() {
        assertThatThrownBy(() -> service.updateWarehouseProgress(11L, 21L, "已沟通需求")).isInstanceOf(BizException.class).hasMessageContaining("无权");
        assertThatThrownBy(() -> service.reject(11L, 21L, "超出服务范围")).isInstanceOf(BizException.class).hasMessageContaining("无权");
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void offlineWarehouseCanAcceptWithVersionAndIdentityGuard() {
        when(customers.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.accept(11L, 20L);
        ArgumentCaptor<Wrapper<Customer>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(customers).update(isNull(), cap.capture());
        LambdaUpdateWrapper<?> update = (LambdaUpdateWrapper<?>) cap.getValue();
        assertThat(update.getSqlSegment()).contains("assigned_warehouse_id", "warehouse_assignment_status", "version");
        assertThat(update.getSqlSet()).contains("warehouse_assignment_status", "version = version + 1");
    }

    @Test
    void acceptedRetryDoesNotWriteAgain() {
        customer.setWarehouseAssignmentStatus(2);
        service.accept(11L, 20L);
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void rejectedMerchantCannotPublishProgress() {
        customer.setWarehouseAssignmentStatus(3);
        assertThatThrownBy(() -> service.updateWarehouseProgress(11L, 20L, "内部跟进")).isInstanceOf(BizException.class).hasMessageContaining("已承接");
    }

    @Test
    void activeContractPreventsAssignmentChange() {
        when(contracts.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.assign(11L, 21L, "调整承接范围")).isInstanceOf(BizException.class).hasMessageContaining("合同");
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void crossProjectAndOfflineUnavailableWarehouseAreRejected() {
        warehouse.setProjectId(2L);
        assertThatThrownBy(() -> service.requireAvailableWarehouse(20L, 1L)).isInstanceOf(BizException.class).hasMessageContaining("跨园区");
        warehouse.setProjectId(1L); warehouse.setJoinStatus(4);
        assertThatThrownBy(() -> service.requireAvailableWarehouse(20L, 1L)).isInstanceOf(BizException.class).hasMessageContaining("尚未上线");
    }

    @Test
    void signedCustomerRemainsSignedWhileAnotherContractIsActive() {
        customer.setStatus(2);
        when(contracts.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThat(service.contractClosed(11L)).isFalse();
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void lastContractClosureReturnsCustomerToFollowing() {
        customer.setStatus(2);
        when(contracts.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(customers.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertThat(service.contractClosed(11L)).isTrue();
        verify(audit).log("customer.contract.closed", "customer", 11L, "有效服务合同已结束，返回跟进");
    }

    @Test
    void signingRequiresAcceptedAssignmentAndCurrentPartner() {
        MktServiceContract contract = new MktServiceContract(); contract.setCustomerId(11L); contract.setWarehouseId(20L); contract.setPartnerId(8L);
        assertThatThrownBy(() -> service.contractEffective(contract)).isInstanceOf(BizException.class).hasMessageContaining("确认承接");
        customer.setWarehouseAssignmentStatus(2); contract.setPartnerId(99L);
        assertThatThrownBy(() -> service.contractEffective(contract)).isInstanceOf(BizException.class).hasMessageContaining("推荐伙伴");
        verify(customers, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void projectionDoesNotExposeInternalRemarkOrReferrer() {
        customer.setRemark("内部佣金协商"); customer.setPhone("13812345678"); customer.setPublicProgress("已完成需求沟通");
        var projection = service.warehouseView(customer);
        assertThat(projection).doesNotContainKeys("remark", "referrerId", "commission");
        assertThat(projection.get("phone")).isEqualTo("138****5678");
        assertThat(projection.get("publicProgress")).isEqualTo("已完成需求沟通");
        customer.setWarehouseAssignmentStatus(3);
        assertThat(service.warehouseView(customer)).doesNotContainKeys("contact", "phone");
    }

    @Test
    void genericCustomerJsonCannotBypassAssignmentAndPublicProgress() throws Exception {
        Customer submitted = new ObjectMapper().readValue("{\"name\":\"合法名字\",\"intendedWarehouseId\":99,\"assignedWarehouseId\":99,\"warehouseAssignmentStatus\":2,\"publicProgress\":\"伪造进度\"}", Customer.class);
        assertThat(submitted.getName()).isEqualTo("合法名字");
        assertThat(submitted.getIntendedWarehouseId()).isNull();
        assertThat(submitted.getAssignedWarehouseId()).isNull();
        assertThat(submitted.getWarehouseAssignmentStatus()).isNull();
        assertThat(submitted.getPublicProgress()).isNull();
    }
}
