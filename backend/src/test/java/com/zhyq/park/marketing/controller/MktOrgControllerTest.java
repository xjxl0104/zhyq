package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktAdminActorService;
import com.zhyq.park.marketing.service.MktCustomerPricingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MktOrgControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final MktServiceContractMapper contracts = mock(MktServiceContractMapper.class);
    private final MktAdminActorService actors = mock(MktAdminActorService.class);
    private final MktCustomerPricingService pricing = mock(MktCustomerPricingService.class);
    private final MktOrgController controller = new MktOrgController(jdbc, promoters, customers, contracts, actors, pricing);

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(MktPromoter.class, Customer.class, MktServiceContract.class)) TableInfoHelper.initTableInfo(assistant, type);
    }

    private static MktPromoter promoter(long id, String name, String position, Long parent) {
        var p = new MktPromoter(); p.setId(id); p.setName(name); p.setPositionCode(position); p.setParentId(parent); p.setStatus(1); return p;
    }

    @Test
    void 组织树带上每人推荐客户数和已签约客户数() {
        when(promoters.selectList(any())).thenReturn(List.of(promoter(4, "丁超", "P4", null), promoter(3, "张三", "P3", 4L), promoter(2, "乙", "P2", 3L)));
        when(jdbc.queryForList(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).contains("DISTINCT")
                ? List.of(Map.<String, Object>of("id", 2L, "n", 1L))
                : List.of(Map.<String, Object>of("id", 2L, "n", 3L), Map.<String, Object>of("id", 3L, "n", 2L)));
        var nodes = controller.tree().getData();
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0)).extracting("id", "customers", "signedCustomers", "parentId").containsExactly(4L, 0, 0, null);
        assertThat(nodes.get(1)).extracting("id", "customers", "signedCustomers").containsExactly(3L, 2, 0);
        assertThat(nodes.get(2)).extracting("id", "customers", "signedCustomers", "parentId").containsExactly(2L, 3, 1, 3L);
    }

    @Test
    void 客户列表分为本人推荐和下一层级推荐并标出是否已签约() {
        when(promoters.selectById(3L)).thenReturn(promoter(3, "张三", "P3", 4L));
        when(promoters.selectList(any())).thenReturn(List.of(promoter(2, "乙", "P2", 3L)));
        Customer own = new Customer(); own.setId(100L); own.setName("自己的客户"); own.setReferrerId(3L);
        Customer below = new Customer(); below.setId(101L); below.setName("乙的客户"); below.setReferrerId(2L);
        when(customers.selectList(any())).thenReturn(List.of(below, own));
        MktServiceContract contract = new MktServiceContract(); contract.setId(9L); contract.setCustomerId(101L);
        contract.setContractNo("CS-9"); contract.setSignMode(2); contract.setStatus(5);
        when(contracts.selectList(any())).thenReturn(List.of(contract));
        var result = controller.customers(3L).getData();
        assertThat(result.nextMembers()).isEqualTo(1);
        assertThat(result.self()).extracting(MktOrgController.OrgCustomer::name, MktOrgController.OrgCustomer::signed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("自己的客户", false));
        assertThat(result.next()).extracting(MktOrgController.OrgCustomer::name, MktOrgController.OrgCustomer::referrerName,
                MktOrgController.OrgCustomer::signed, MktOrgController.OrgCustomer::contractNo)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("乙的客户", "乙", true, "CS-9"));
    }

    @Test
    void 调整层级以后台账号关联的P4身份执行() {
        when(actors.currentPromoter()).thenReturn(promoter(4, "丁超", "P4", null));
        controller.position(2L, new MktOrgController.PositionInput("P3"));
        verify(pricing).setTeamPosition(4L, 2L, "P3");
    }

    @Test
    void 后台账号没有关联伙伴时不能调整并说明原因() {
        when(actors.currentPromoter()).thenReturn(null);
        assertThatThrownBy(() -> controller.position(2L, new MktOrgController.PositionInput("P3")))
                .isInstanceOf(BizException.class).hasMessageContaining("未关联伙伴档案");
        assertThatThrownBy(() -> controller.positionPreview(2L, "P3")).isInstanceOf(BizException.class);
        verifyNoInteractions(pricing);
    }
}
