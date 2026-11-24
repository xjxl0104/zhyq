package com.zhyq.park.marketing.wh;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhWorkspaceServiceTest {
    MktWarehouseContactMapper contacts=mock(MktWarehouseContactMapper.class);
    MktWarehouseMapper warehouses=mock(MktWarehouseMapper.class);
    CustomerMapper customers=mock(CustomerMapper.class);
    WhIdentityService identities=mock(WhIdentityService.class);
    MktCustomerAssignmentService assignments=mock(MktCustomerAssignmentService.class);
    MktCustomerProgressService progress=mock(MktCustomerProgressService.class);
    MktAuditService audit=mock(MktAuditService.class);
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    WhWorkspaceService service=new WhWorkspaceService(contacts,warehouses,customers,identities,assignments,progress,audit,jdbc,mock(com.zhyq.park.marketing.quote.QuoteIntakeService.class));
    MktWarehouseContact member;MktWarehouse warehouse;Customer customer;
    @BeforeAll static void metadata(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");for(Class<?> c:List.of(MktWarehouse.class,MktWarehouseContact.class,Customer.class))TableInfoHelper.initTableInfo(a,c);}
    @BeforeEach void setup(){
        warehouse=new MktWarehouse();warehouse.setId(11L);warehouse.setJoinStatus(5);warehouse.setProjectId(7L);warehouse.setTenantId(1L);warehouse.setPhone("13800138000");
        member=new MktWarehouseContact();member.setId(5L);member.setWarehouseId(11L);member.setProjectId(7L);member.setTenantId(1L);member.setRole("member");member.setStatus(1);member.setName("张三");member.setPhone("13900139000");
        customer=new Customer();customer.setId(9L);customer.setAssignedWarehouseId(11L);customer.setWarehouseSalespersonId(5L);customer.setWarehouseAssignmentStatus(2);customer.setWarehouseSalesStage(1);customer.setProjectId(7L);customer.setTenantId(1L);customer.setVersion(1);
        when(contacts.selectById(5L)).thenReturn(member);when(warehouses.selectById(11L)).thenReturn(warehouse);when(warehouses.selectForUpdate(11L)).thenReturn(warehouse);when(customers.selectById(9L)).thenReturn(customer);when(customers.selectForUpdate(9L)).thenReturn(customer);
        login("member");
    }
    void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wh:11:contact:5",null,List.of(new SimpleGrantedAuthority("ROLE_WH"),new SimpleGrantedAuthority("ROLE_WH_"+role.toUpperCase()))));}
    void owner(){member.setRole("owner");member.setPhone(warehouse.getPhone());login("owner");}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void staffCannotAcceptAndOwnerMustAcceptBeforeDelegation(){
        customer.setWarehouseAssignmentStatus(1);
        assertThatThrownBy(()->service.accept(9L)).isInstanceOf(BizException.class);
        owner();
        assertThatThrownBy(()->service.assignStaff(9L,6L)).isInstanceOf(BizException.class).hasMessageContaining("先由云仓主账号确认承接");
        service.accept(9L);
        verify(assignments).accept(9L,11L);
        verify(customers,never()).update(any(),any());
    }
    @Test void memberReadsAssignedCustomerButNotOtherMembersOrOtherWarehouses(){
        assertThat(service.requireCustomer(9L,false)).isSameAs(customer);
        customer.setWarehouseSalespersonId(6L);assertThatThrownBy(()->service.requireCustomer(9L,false)).isInstanceOf(BizException.class);
        customer.setWarehouseSalespersonId(5L);customer.setAssignedWarehouseId(99L);assertThatThrownBy(()->service.requireCustomer(9L,false)).isInstanceOf(BizException.class);
    }
    @Test void ownerCannotReadOtherWarehouseEvenWithForgedCustomerId(){owner();customer.setAssignedWarehouseId(99L);assertThatThrownBy(()->service.detail(9L)).isInstanceOf(BizException.class);verifyNoInteractions(jdbc,assignments);}
    @Test void disabledContactAndTenantMismatchAreDenied(){member.setStatus(0);assertThatThrownBy(service::current).isInstanceOf(BizException.class);member.setStatus(1);member.setTenantId(2L);assertThatThrownBy(service::current).isInstanceOf(BizException.class);}
    @Test void memberCannotCreateStaffOrAssignCustomers(){assertThatThrownBy(()->service.saveStaff(null,Map.of("name","李四","phone","13700137000"))).isInstanceOf(BizException.class);assertThatThrownBy(()->service.assignStaff(9L,6L)).isInstanceOf(BizException.class);verifyNoInteractions(identities);}
    @Test void ownerCannotEditOtherWarehouseOrOwnerContact(){owner();var other=new MktWarehouseContact();other.setId(6L);other.setWarehouseId(99L);other.setRole("member");when(contacts.selectById(6L)).thenReturn(other);assertThatThrownBy(()->service.saveStaff(6L,Map.of("name","李四","phone","13700137000"))).isInstanceOf(BizException.class);assertThatThrownBy(()->service.saveStaff(5L,Map.of("name","李四","phone","13700137000"))).isInstanceOf(BizException.class);verify(contacts,never()).update(any(),any());}
    @Test void memberCannotChangeOwnPhoneOrWarehouse(){assertThatThrownBy(()->service.updateProfile(Map.of("name","张三","phone","13700137000"))).isInstanceOf(BizException.class);assertThatThrownBy(()->service.updateProfile(Map.of("name","张三","warehouseName","别人的仓"))).isInstanceOf(BizException.class);verify(contacts,never()).update(any(),any());}
    @Test void memberListScopeAlwaysContainsSalespersonWarehouseProjectAndTenant(){var q=service.customerScope(service.current());assertThat(q.getSqlSegment()).contains("assigned_warehouse_id","warehouse_salesperson_id","project_id","tenant_id");assertThat(q.getParamNameValuePairs()).containsValues(11L,5L,7L,1L);}
    @Test void ownerCannotAssignDisabledOrOtherWarehouseStaff(){owner();var staff=new MktWarehouseContact();staff.setId(6L);staff.setWarehouseId(11L);staff.setRole("member");staff.setStatus(0);when(contacts.selectById(6L)).thenReturn(staff);assertThatThrownBy(()->service.assignStaff(9L,6L)).isInstanceOf(BizException.class);staff.setStatus(1);staff.setWarehouseId(99L);assertThatThrownBy(()->service.assignStaff(9L,6L)).isInstanceOf(BizException.class);verify(customers,never()).update(any(),any());}
    @Test void progressRequiresOwnCustomerAndAppendsBothPublicAndWarehouseHistory(){when(customers.update(isNull(),any())).thenReturn(1);when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);service.updateStage(9L,4,"首批入仓");verify(jdbc).update(startsWith("INSERT INTO crm_warehouse_customer_progress"),eq(11L),eq(9L),eq(5L),eq(5L),eq("张三"),eq(4),eq("货品入仓：首批入仓"),eq(1L),any());verify(assignments).updateWarehouseProgress(9L,11L,"货品入仓：首批入仓",null);customer.setWarehouseSalespersonId(6L);assertThatThrownBy(()->service.updateStage(9L,5,"发货")).isInstanceOf(BizException.class);}
    @Test void stageBoundsRejectBeforeDatabaseMutation(){assertThatThrownBy(()->service.updateStage(9L,6,"错误")).isInstanceOf(BizException.class);verifyNoInteractions(customers,jdbc);}
}
