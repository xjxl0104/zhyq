package com.zhyq.park.marketing.controller;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.marketing.wh.WhIdentityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktWarehouseOwnerUpdateTest {
    MktWarehouseMapper warehouses=mock(MktWarehouseMapper.class);
    WhIdentityService identities=mock(WhIdentityService.class);
    MktWarehouseController controller=new MktWarehouseController(warehouses,mock(MktWarehouseOnboardingMapper.class),mock(MktServiceContractMapper.class),mock(MktWarehouseOnboardingService.class),mock(MktAuditService.class),mock(MktWarehouseFileService.class),mock(MktWarehouseDeletionService.class),mock(JdbcTemplate.class));
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),MktWarehouse.class);}
    @BeforeEach void setup(){ReflectionTestUtils.setField(controller,"identities",identities);}
    MktWarehouse profile(){var w=new MktWarehouse();w.setId(11L);w.setName("云仓");w.setContact("张三");w.setPhone("13900139000");w.setRegion("广东");w.setAddress("园区");return w;}
    @Test void adminProfileUpdateSynchronizesOwnerBeforeWritingWarehouse(){
        var w=profile();when(identities.maintainOwner(11L,"张三","13900139000",true)).thenReturn(w);when(warehouses.update(isNull(),any())).thenReturn(1);
        controller.update(w);
        var order=inOrder(identities,warehouses);order.verify(identities).maintainOwner(11L,"张三","13900139000",true);order.verify(warehouses).update(isNull(),any());
    }
    @Test void phoneConflictCannotLeaveOnlyWarehouseChanged(){
        doThrow(new BizException(409,"该手机号已关联云仓账号")).when(identities).maintainOwner(11L,"张三","13900139000",true);
        assertThatThrownBy(()->controller.update(profile())).isInstanceOf(BizException.class);verify(warehouses,never()).update(any(),any());
    }
}
