package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerPricing;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktCustomerPricingMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.MktCustomerPricingService.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MktCustomerPricingServiceTest {
    @Mock MktCustomerPricingMapper pricingMapper;
    @Mock CustomerMapper customerMapper;
    @Mock MktPromoterMapper promoterMapper;
    @Mock MktAuditService audit;
    @Mock MktPositionReviewService positionService;
    MktCustomerPricingService service;
    Map<Long, MktPromoter> team;

    @BeforeAll static void metadata() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(MktCustomerPricing.class, MktPromoter.class, Customer.class)) TableInfoHelper.initTableInfo(a,c);
    }
    @BeforeEach void setup() {
        service = new MktCustomerPricingService(pricingMapper, customerMapper, promoterMapper, audit, new ObjectMapper(), positionService);
        team = new HashMap<>();
        team.put(4L, promoter(4L, "P4", null)); team.put(3L, promoter(3L, "P3", 4L));
        team.put(2L, promoter(2L, "P2", 3L)); team.put(1L, promoter(1L, "P1", 2L));
        team.put(8L, promoter(8L, "P4", null));
        lenient().when(promoterMapper.selectById(anyLong())).thenAnswer(i -> team.get(i.getArgument(0)));
        lenient().when(promoterMapper.selectForUpdate(anyLong())).thenAnswer(i -> team.get(i.getArgument(0)));
        var customer = new Customer(); customer.setId(10L); customer.setName("测试品牌"); customer.setReferrerId(1L);
        lenient().when(customerMapper.selectById(10L)).thenReturn(customer);
        lenient().when(pricingMapper.insert(any(MktCustomerPricing.class))).thenAnswer(i -> { ((MktCustomerPricing)i.getArgument(0)).setId(21L); return 1; });
    }

    @Test void p4CanSetArbitraryAmountsForDeeplyReferredCustomerWithCompanyRemainder() {
        var view = service.save(4L,10L,input("0.300", "0.023",1L,"0.077",2L,"0.001"));
        assertThat(view.totalPerOrder()).isEqualByComparingTo("0.3");
        assertThat(view.ownerPerOrder()).isEqualByComparingTo("0.023");
        assertThat(view.allocatedPerOrder()).isEqualByComparingTo("0.078");
        assertThat(view.companyPerOrder()).isEqualByComparingTo("0.199");
        assertThat(view.beneficiaries()).extracting(Beneficiary::positionCode).containsExactly("P1","P2");
        verify(pricingMapper).insert(any(MktCustomerPricing.class));
    }
    @Test void noBeneficiariesAndZeroOwnerAreExplicitlyAllowed() {
        var view = service.save(4L,10L,new PricingInput(bd("0.001"), BigDecimal.ZERO, List.of()));
        assertThat(view.companyPerOrder()).isEqualByComparingTo("0.001");
        assertThat(view.beneficiaries()).isEmpty();
    }
    @Test void lowerRolesFrozenP4AndUnrelatedP4CannotSetPrices() {
        assertThatThrownBy(() -> service.save(1L,10L,input("1","0",1L,"0",2L,"0"))).isInstanceOf(BizException.class).hasMessageContaining("P4");
        team.get(4L).setStatus(2);
        assertThatThrownBy(() -> service.getForP4(4L,10L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getForP4(8L,10L)).isInstanceOf(BizException.class).hasMessageContaining("邀请体系");
        verify(pricingMapper, never()).insert(any(MktCustomerPricing.class));
    }
    @Test void nearestP4OwnsCustomerAndCannotBeOverriddenByAncestorP4() {
        team.get(3L).setPositionCode("P4");
        assertThatThrownBy(() -> service.getForP4(4L,10L)).isInstanceOf(BizException.class).hasMessageContaining("邀请体系");
        assertThat(service.getForP4(3L,10L).canEdit()).isTrue();
    }
    @Test void enforcesPrecisionCountBudgetAndExplicitOwnerAmount() {
        assertThatThrownBy(() -> service.save(4L,10L,input("1","0",1L,"0.0001",2L,"0"))).hasMessageContaining("3 位小数");
        assertThatThrownBy(() -> service.save(4L,10L,input("0.1","0.05",1L,"0.05",2L,"0.001"))).hasMessageContaining("超过");
        assertThatThrownBy(() -> service.save(4L,10L,new PricingInput(bd("1"),null,List.of()))).hasMessageContaining("P4 本人金额");
        assertThatThrownBy(() -> service.save(4L,10L,new PricingInput(bd("1"),bd("0"),List.of(new BeneficiaryInput(1L,bd("0")),new BeneficiaryInput(2L,bd("0")),new BeneficiaryInput(3L,bd("0")))))).hasMessageContaining("2 名");
        verify(pricingMapper, never()).insert(any(MktCustomerPricing.class));
    }
    @Test void rejectsDuplicateSelfAndOutsideRecipients() {
        assertThatThrownBy(() -> service.save(4L,10L,input("1","0",1L,"0",1L,"0"))).hasMessageContaining("重复");
        assertThatThrownBy(() -> service.save(4L,10L,input("1","0",4L,"0",1L,"0"))).hasMessageContaining("本人");
        assertThatThrownBy(() -> service.save(4L,10L,input("1","0",8L,"0",1L,"0"))).hasMessageContaining("邀请体系");
        verify(pricingMapper, never()).insert(any(MktCustomerPricing.class));
    }
    @Test void p4MayExplicitlyAssignAnyRoleOnlyInsideOwnTeam() {
        service.setTeamPosition(4L,1L,"P4");
        verify(positionService).changeManually(eq(1L),eq("P4"),anyString());
        assertThatThrownBy(() -> service.setTeamPosition(4L,8L,"P1")).hasMessageContaining("邀请体系");
        assertThatThrownBy(() -> service.setTeamPosition(4L,4L,"P1")).hasMessageContaining("其他");
        assertThatThrownBy(() -> service.setTeamPosition(1L,2L,"P4")).hasMessageContaining("P4");
    }

    @Test void noConfigurationNeverFallsBackToGradeOrPositionPercentage() {
        assertThatThrownBy(() -> service.forAccrual(10L)).isInstanceOf(BizException.class).hasMessageContaining("配置后重试");
        var view = service.getForAdmin(10L);
        assertThat(view.configured()).isFalse(); assertThat(view.canEdit()).isFalse(); assertThat(view.totalPerOrder()).isNull();
    }
    @Test void accrualRejectsReparentedOrFrozenRecipientInsteadOfReassigningMoney() throws Exception {
        MktCustomerPricing saved = new MktCustomerPricing(); saved.setId(21L); saved.setCustomerId(10L); saved.setOwnerPromoterId(4L);
        saved.setTotalPerOrder(bd("0.300")); saved.setOwnerPerOrder(bd("0.023"));
        saved.setBeneficiariesJson(new ObjectMapper().writeValueAsString(List.of(new Beneficiary(2L,"伙伴2","P2",bd("0.001")))));
        when(pricingMapper.selectOne(any())).thenReturn(saved);
        assertThat(service.forAccrual(10L).companyPerOrder()).isEqualByComparingTo("0.276");
        team.get(2L).setStatus(2);
        assertThatThrownBy(() -> service.forAccrual(10L)).hasMessageContaining("正常");
    }
    @Test void changingPriceAppendsVersionRatherThanMutatingPastConfiguration() {
        service.save(4L,10L,new PricingInput(bd("0.100"),bd("0.010"),List.of()));
        service.save(4L,10L,new PricingInput(bd("0.200"),bd("0.020"),List.of()));
        verify(pricingMapper,times(2)).insert(any(MktCustomerPricing.class));
        verify(pricingMapper,never()).updateById(any(MktCustomerPricing.class));
    }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static PricingInput input(String total,String owner,Long a,String amountA,Long b,String amountB) {
        return new PricingInput(bd(total),bd(owner),List.of(new BeneficiaryInput(a,bd(amountA)),new BeneficiaryInput(b,bd(amountB))));
    }
    private static MktPromoter promoter(Long id,String position,Long parent) {
        var p = new MktPromoter(); p.setId(id); p.setPositionCode(position); p.setParentId(parent); p.setStatus(1); p.setIsInternal(0); p.setName("伙伴"+id); return p;
    }
}
