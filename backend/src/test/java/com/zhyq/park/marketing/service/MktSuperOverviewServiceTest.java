package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.admin.MktAccountControl;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import static com.zhyq.park.marketing.service.MktSuperOverviewService.map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktSuperOverviewServiceTest {
    final MktAccountControl access=mock(MktAccountControl.class);
    final MktPromoterMapper people=mock(MktPromoterMapper.class);
    final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    final MktSuperOverviewService service=new MktSuperOverviewService(access,people,jdbc);

    @Test void bothEntryPointsCheckLiveGrantBeforeAnyBusinessRead() {
        when(access.requireSuperAdmin()).thenThrow(new BizException(403,"denied"));
        assertThatThrownBy(()->service.overview("partners",null,"p4",null,null,null,1,20)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.customer(101L,null,null)).isInstanceOf(BizException.class);
        verify(access,times(2)).requireSuperAdmin(); verifyNoInteractions(people,jdbc);
    }
    @Test void disabledAdministratorCannotReadEvenWithStaleGrant() {
        when(access.requireSuperAdmin()).thenReturn(11L);
        MktPromoter actor=new MktPromoter();actor.setId(11L);actor.setStatus(2);
        when(people.selectById(11L)).thenReturn(actor);
        assertThatThrownBy(()->service.overview("warehouses",null,"p4",null,null,null,1,20)).isInstanceOf(BizException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void allQueriesUseActorsTenantAndUntrustedInputNeverEntersSql() {
        when(access.requireSuperAdmin()).thenReturn(11L);
        MktPromoter actor=new MktPromoter();actor.setId(11L);actor.setStatus(1);actor.setTenantId(7L);
        when(people.selectById(11L)).thenReturn(actor);
        List<String> sql=new ArrayList<>();
        when(jdbc.queryForList(anyString(),org.mockito.ArgumentMatchers.<String,Object>anyMap())).thenAnswer(i->{
            sql.add(i.getArgument(0));Map<String,Object> args=i.getArgument(1);
            assertThat(args.get("tenantId")).isEqualTo(7L);return List.of();
        });
        var result=service.overview("partners",null,"all","' OR 1=1 --", "2026-01-01","2026-01-31",1,20);
        assertThat(result.get("records")).isEqualTo(List.of());
        assertThat(sql).hasSize(7).allMatch(s->s.contains(":tenantId")&&!s.contains("OR 1=1"));
    }
    @Test void datesAndPaginationRejectInvalidOrUnboundedInput() {
        assertThatThrownBy(()->MktSuperOverviewService.range("2026-02-01","2026-01-01")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->MktSuperOverviewService.range("2026-01-01","9999-01-01")).isInstanceOf(BizException.class);
        assertThatThrownBy(()->MktSuperOverviewService.range("not-a-date",null)).isInstanceOf(BizException.class);
        assertThat(MktSuperOverviewService.range(null,null).start().toString()).isEqualTo("2000-01-01");
    }
    @Test void independentFactsDoNotMultiplyVolumeOrMoneyByBeneficiaries() {
        var data=fixture();var summary=data.summary(data.scope("partners",null));
        assertThat(summary.get("shipmentVolume")).isEqualTo("15");
        assertThat(summary.get("commissionTotal")).isEqualTo("0.30");
        assertThat(summary.get("partnerCommission")).isEqualTo("0.270");
        assertThat(summary.get("pendingCommission")).isEqualTo("0.090");
        assertThat(summary.get("settledCommission")).isEqualTo("0.180");
    }
    @Test void p4ScopeIncludesDeepReferralCustomersAndDoesNotDuplicateGlobalTotals() {
        var data=fixture();
        assertThat(data.descendants(1L)).containsExactlyInAnyOrder(1L,2L,3L);
        assertThat(data.scope("partners",1L).customers()).containsExactly(101L);
        assertThat(data.summary(data.scope("partners",1L)).get("shipmentVolume")).isEqualTo("15");
        assertThat(data.summary(data.scope("partners",4L)).get("shipmentVolume")).isEqualTo("0");
        assertThat(data.summary(data.scope("partners",null)).get("p4Count")).isEqualTo(2L);
    }
    @Test void warehouseScopeSeparatesTwoWarehousesServingSameCustomer() {
        var data=fixture();
        var first=data.summary(data.scope("warehouses",201L));var second=data.summary(data.scope("warehouses",202L));
        assertThat(first.get("shipmentVolume")).isEqualTo("10");assertThat(second.get("shipmentVolume")).isEqualTo("5");
        assertThat(first.get("partnerCommission")).isEqualTo("0.180");assertThat(second.get("partnerCommission")).isEqualTo("0.090");
    }
    @Test void contractBreakdownExcludesOtherContractsAndNonContractIncome() {
        var data=fixture();data.ledger.add(map("customerId",101L,"contractId",null,"status",3,"amount","2.000"));
        var scope=new MktSuperOverviewService.Scope(Set.of(1L),Set.of(101L),Set.of(301L),null,false,true,"");
        assertThat(data.summary(scope).get("partnerCommission")).isEqualTo("0.180");
        assertThat(data.summary(data.scope("partners",null)).get("partnerCommission")).isEqualTo("2.270");
    }
    @Test void unknownOrCrossTenantIdentifiersAreRejectedInsteadOfReturningGlobalData() {
        var data=fixture();
        assertThatThrownBy(()->data.scope("partners",999L)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->data.scope("warehouses",999L)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->data.scope("partners",0L)).isInstanceOf(BizException.class);
    }
    @Test void malformedParentCyclesTerminateAndLargeVolumesStayExact() {
        var data=fixture();data.partnerById.get(1L).put("parentId",3L);
        assertThat(data.descendants(1L)).hasSize(3);
        data.volume.clear();data.volume.add(map("customer_id",101L,"contract_id",301L,"volume","9007199254740993"));
        assertThat(data.summary(data.scope("partners",null)).get("shipmentVolume")).isEqualTo("9007199254740993");
    }
    static MktSuperOverviewService.Data fixture() {
        return new MktSuperOverviewService.Data(new ArrayList<>(List.of(
                map("id",1L,"name","P4-A","positionCode","P4","parentId",null),
                map("id",2L,"name","P3","positionCode","P3","parentId",1L),
                map("id",3L,"name","P1","positionCode","P1","parentId",2L),
                map("id",4L,"name","P4-B","positionCode","P4","parentId",null))),
            List.of(map("id",101L,"name","客户","referrerId",3L,"assignedWarehouseId",201L)),
            List.of(map("id",301L,"customerId",101L,"warehouseId",201L,"partnerId",3L,"status",4),
                    map("id",302L,"customerId",101L,"warehouseId",202L,"partnerId",3L,"status",5)),
            List.of(map("id",201L,"name","云仓A"),map("id",202L,"name","云仓B")),
            new ArrayList<>(List.of(map("customer_id",101L,"contract_id",301L,"volume",10),map("customer_id",101L,"contract_id",302L,"volume",5))),
            List.of(map("customerId",101L,"contractId",301L,"total","0.20"),map("customerId",101L,"contractId",302L,"total","0.10")),
            new ArrayList<>(List.of(map("customerId",101L,"contractId",301L,"promoterId",1L,"status",3,"amount","0.060"),
                    map("customerId",101L,"contractId",301L,"promoterId",3L,"status",3,"amount","0.120"),
                    map("customerId",101L,"contractId",302L,"promoterId",3L,"status",2,"amount","0.090"))));
    }
}
