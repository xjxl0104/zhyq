package com.zhyq.park.marketing.service;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktPromoterAccountServiceTest {
    MktPromoterMapper promoters=mock(MktPromoterMapper.class);
    MktPromoterAccountMapper accounts=mock(MktPromoterAccountMapper.class);
    MktWithdrawalMapper withdrawals=mock(MktWithdrawalMapper.class);
    FieldEncryptionService encryption=mock(FieldEncryptionService.class);
    MktAuditService audit=mock(MktAuditService.class);
    MktPartnerNoticeService notices=mock(MktPartnerNoticeService.class);
    MktPromoterAccountService service=new MktPromoterAccountService(promoters,accounts,withdrawals,encryption,audit,notices);
    @BeforeAll static void init(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"");TableInfoHelper.initTableInfo(a,MktPromoter.class);TableInfoHelper.initTableInfo(a,MktPromoterAccount.class);TableInfoHelper.initTableInfo(a,MktWithdrawal.class);}
    @BeforeEach void setup(){var p=new MktPromoter();p.setId(1L);p.setStatus(1);when(promoters.selectForUpdate(1L)).thenReturn(p);}
    @Test void submittingDoesNotSelfApprove(){
        service.submit(1L,body());
        verify(accounts).insert(argThat((MktPromoterAccount a)->a.getReviewStatus()==0 && a.getVerifiedAt()==null));
        verify(audit).log(eq("promoter.account.submit"),eq("promoter"),eq(1L),anyString());
    }
    @Test void refusesAccountChangeWhileWithdrawalPending(){
        when(withdrawals.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(()->service.submit(1L,body())).isInstanceOf(BizException.class).hasMessageContaining("处理中提现");
        verify(accounts,never()).insert(any(MktPromoterAccount.class));
    }
    @Test void staleReviewNeverMarksPromoterApproved(){
        MktPromoterAccount a=new MktPromoterAccount();a.setId(10L);a.setVersion(3);a.setReviewStatus(0);when(accounts.selectOne(any())).thenReturn(a);
        when(accounts.update(isNull(),any())).thenReturn(0);
        assertThatThrownBy(()->service.review(1L,2,true,"资料已核对","ops")).isInstanceOf(BizException.class).hasMessageContaining("已变更");
        verify(promoters,never()).update(isNull(),any());verifyNoInteractions(notices);
    }
    @Test void ordinaryProfileDoesNotReturnIdentityOrAccountCiphertext(){
        MktPromoterAccount a=new MktPromoterAccount();a.setIdNoEnc("secret-id");a.setAccountNoEnc("secret-bank");a.setAccountTail("1234");when(accounts.selectOne(any())).thenReturn(a);
        assertThat(service.view(1L,false)).doesNotContainKeys("idNo","accountNo","idNoEnc","accountNoEnc");
        verifyNoInteractions(encryption);
    }
    private Map<String,String> body(){return Map.of("realName","演示收款人","idNo","11010519491231002X","accountType","2","accountNo","6222020000000000","bankName","测试银行");}
}
