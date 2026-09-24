package com.zhyq.park.crm;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.controller.CustomerController;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.*;
import com.zhyq.park.marketing.service.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CustomerMarketingGuardTest {
    CustomerMapper customers=mock(CustomerMapper.class);
    CustomerController controller=new CustomerController(customers,mock(LeadMapper.class),mock(MktCustomerAssignmentService.class),mock(MktLockService.class));
    @Test void ordinarySignCannotFakeMarketingContractCompletion(){
        Customer c=new Customer();c.setId(1L);c.setReferrerId(9L);when(customers.selectForUpdate(1L)).thenReturn(c);
        assertThatThrownBy(()->controller.sign(1L)).isInstanceOf(BizException.class).hasMessageContaining("真实合同");
        verify(customers,never()).update(isNull(),any());
    }
    @Test void ordinaryEditCannotReassignReferrer(){
        Customer c=new Customer();c.setId(1L);c.setReferrerId(9L);when(customers.selectForUpdate(1L)).thenReturn(c);
        Customer body=new Customer();body.setId(1L);body.setReferrerId(10L);
        assertThatThrownBy(()->controller.update(body)).isInstanceOf(BizException.class).hasMessageContaining("全民营销");
        verify(customers,never()).updateById(any(Customer.class));
    }
    @Test void ordinaryDeleteRetainsReferredCustomer(){
        Customer c=new Customer();c.setId(1L);c.setReferrerId(9L);when(customers.selectForUpdate(1L)).thenReturn(c);
        assertThatThrownBy(()->controller.delete(1L)).isInstanceOf(BizException.class).hasMessageContaining("保留档案");
        verify(customers,never()).deleteById(anyLong());
    }
}
