package com.zhyq.park.crm.service;

import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeadPartnerReferenceTest {
    private final LeadMapper leads = mock(LeadMapper.class);
    private final MktPromoterMapper promoters = mock(MktPromoterMapper.class);
    private final LeadService service = new LeadService(leads, promoters);

    @Test void leadCannotLinkToPartnerDeletedBeforeRelationLockWasObtained() {
        Lead lead = new Lead(); lead.setContact("测试客户"); lead.setReferrerId(7L);
        assertThatThrownBy(() -> service.create(lead)).hasMessageContaining("推荐伙伴不存在");
        verify(promoters).selectForUpdate(7L);
        verifyNoInteractions(leads);
    }

    @Test void validPartnerIsLockedBeforeLeadIsInserted() {
        Lead lead = new Lead(); lead.setContact("测试客户"); lead.setReferrerId(7L);
        MktPromoter p = new MktPromoter(); p.setId(7L); p.setStatus(1);
        when(promoters.selectForUpdate(7L)).thenReturn(p);
        when(leads.insert(any(Lead.class))).thenAnswer(call -> { ((Lead) call.getArgument(0)).setId(9L); return 1; });
        assertThat(service.create(lead)).isEqualTo(9L);
        var ordered = inOrder(promoters, leads);
        ordered.verify(promoters).selectForUpdate(7L);
        ordered.verify(leads).maxNoSeq("KH-");
        ordered.verify(leads).insert(lead);
    }

    @Test void ordinaryLeadDoesNotRequirePartner() {
        Lead lead = new Lead(); lead.setContact("测试客户");
        service.create(lead);
        verifyNoInteractions(promoters);
        verify(leads).insert(lead);
    }
}
