package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktCustomerCreateControllerTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    final ProjectMapper projects = mock(ProjectMapper.class);
    final MktReferralService referrals = mock(MktReferralService.class);
    final MktCompetitionProtectionService protection = mock(MktCompetitionProtectionService.class);
    final MktCustomerAssignmentService assignment = mock(MktCustomerAssignmentService.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final MktCustomerIntakeGuard intakeGuard = mock(MktCustomerIntakeGuard.class);
    final MktCustomerCreateController controller = new MktCustomerCreateController(customers, promoters, projects, referrals, protection, assignment, audit, intakeGuard);
    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Customer.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MktPromoter.class);
        Project project = new Project(); project.setId(1L); project.setTenantId(1L);
        when(projects.selectById(1L)).thenReturn(project);
        when(customers.insert(any(Customer.class))).thenAnswer(inv -> { inv.<Customer>getArgument(0).setId(42L); return 1; });
    }
    Map<String,Object> body() { return new HashMap<>(Map.of("projectId", 1L, "name", "新增客户", "serviceType", 2)); }
    @Test void directCustomerKeepsPhoneOptionalAndIgnoresPrivilegeFields() {
        Map<String,Object> body = body();
        body.put("phone", "  "); body.put("id", 999L); body.put("tenantId", 7L); body.put("status", 2);
        body.put("remark", "要求纸箱包装");
        body.put("assignedWarehouseId", 8L); body.put("requirements", Map.of("salesPlatforms", List.of("天猫"), "invoiceRequired", false));
        controller.create(body);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).insert(saved.capture());
        Customer customer = saved.getValue();
        assertNull(customer.getPhone()); assertEquals(1L, customer.getTenantId());
        assertEquals(1L, customer.getProjectId()); assertEquals(1, customer.getStatus());
        assertNull(customer.getReferrerId()); assertNull(customer.getAssignedWarehouseId());
        assertEquals("要求纸箱包装", customer.getIntakeRemark());
        assertEquals(false, MktReferralRequirements.parse(customer.getFulfillmentRequirements()).get("invoiceRequired"));
        verify(protection).assertUnprotected(1L, "新增客户", null);
        verify(intakeGuard).lock(1L);
        verifyNoInteractions(referrals);
    }
    @Test void rejectsInvalidPhoneDuplicateAndProtectedCustomerBeforeInsert() {
        Map<String,Object> body = body(); body.put("phone", "123");
        assertThrows(BizException.class, () -> controller.create(body));
        body.remove("phone"); when(customers.selectCount(any())).thenReturn(1L);
        assertThrows(BizException.class, () -> controller.create(body));
        when(customers.selectCount(any())).thenReturn(0L);
        doThrow(new BizException("受保护客户")).when(protection).assertUnprotected(1L, "新增客户", null);
        assertThrows(BizException.class, () -> controller.create(body));
        verify(customers, never()).insert(any(Customer.class));
    }
    @Test void chosenPartnerUsesSharedReferralRulesAndRejectsCrossProject() {
        MktPromoter partner = new MktPromoter(); partner.setId(6L); partner.setStatus(1); partner.setProjectId(2L); partner.setTenantId(1L);
        when(promoters.selectForUpdate(6L)).thenReturn(partner);
        Map<String,Object> body = body(); body.put("referrerId", 6L);
        assertThrows(BizException.class, () -> controller.create(body));
        verifyNoInteractions(referrals);
        partner.setProjectId(1L);
        when(referrals.createForProject(6L, 1L, body)).thenReturn(Map.of("customerId", 17L, "lockStatus", 2));
        controller.create(body);
        verify(referrals).createForProject(6L, 1L, body);
        verify(customers, never()).insert(any(Customer.class));
    }
    @Test void rejectsMissingProjectAndUnrelatedTenantBeforeWrite() {
        Map<String,Object> body = body(); body.remove("projectId");
        assertThrows(BizException.class, () -> controller.create(body));
        Project other = new Project(); other.setId(2L); other.setTenantId(88L);
        when(projects.selectById(2L)).thenReturn(other); body.put("projectId", 2L);
        assertThrows(BizException.class, () -> controller.create(body));
        verify(customers, never()).insert(any(Customer.class));
    }
}
