package com.zhyq.park.marketing.service;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class WhPhoneClaimServiceTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    MktWarehouseMapper warehouses=mock(MktWarehouseMapper.class);
    MktWarehouseContactMapper contacts=mock(MktWarehouseContactMapper.class);
    WhPhoneClaimService claims=new WhPhoneClaimService(jdbc,warehouses,contacts);
    @BeforeAll static void metadata(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,MktWarehouse.class);TableInfoHelper.initTableInfo(a,MktWarehouseContact.class);}
    @Test void lockIsAcquiredBeforeEitherOccupancyCheck(){
        claims.reserve("13900139000",null,null);
        var order=inOrder(jdbc,warehouses,contacts);
        order.verify(jdbc).update(anyString(),eq("13900139000"));
        order.verify(jdbc).queryForObject(contains("FOR UPDATE"),eq(String.class),eq("13900139000"));
        order.verify(warehouses).selectList(argThat(q->q.getSqlSegment().contains("FOR UPDATE")));
        order.verify(contacts).selectList(argThat(q->q.getSqlSegment().contains("FOR UPDATE")));
    }
    @Test void staffClaimCommittedBeforePasswordOrAdminRegistrationCausesConflict(){when(contacts.selectList(any())).thenReturn(java.util.List.of(new MktWarehouseContact()));assertThatThrownBy(()->claims.reserve("13900139000",null,null)).isInstanceOf(BizException.class).hasMessageContaining("已关联");}
    @Test void adminOwnerUpdateExcludesOnlyTheSameWarehouseAndOwner(){
        when(warehouses.selectList(any())).thenAnswer(inv->{var q=(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)inv.getArgument(0);assertThat(q.getSqlSegment()).contains("id <>","FOR UPDATE");assertThat(q.getParamNameValuePairs()).containsValues(11L,"13800138000");return java.util.List.of();});
        when(contacts.selectList(any())).thenAnswer(inv->{var q=(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)inv.getArgument(0);assertThat(q.getSqlSegment()).contains("id <>","FOR UPDATE");assertThat(q.getParamNameValuePairs()).containsValues(5L,"13800138000");return java.util.List.of();});
        claims.reserve("13800138000",11L,5L);
    }
}
