package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktReferralLinkServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final MktPromoterMapper partners = mock(MktPromoterMapper.class);
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final MktReferralService referrals = mock(MktReferralService.class);
    final MktReferralLinkService service = new MktReferralLinkService(jdbc, partners, warehouses, referrals, new ObjectMapper());
    final String token = "0123456789abcdef0123456789abcdef";
    final Map<String,Object> body = new HashMap<>(Map.of("name", "客户", "agreed", true, "submissionId", "request-0123456789", "referrerId", 999L));
    MktPromoter partner;
    @BeforeEach void setup() {
        partner = new MktPromoter(); partner.setId(7L); partner.setStatus(1); partner.setTenantId(1L); partner.setName("伙伴");
        when(partners.selectForUpdate(7L)).thenReturn(partner); when(partners.selectById(7L)).thenReturn(partner);
        when(jdbc.query(startsWith("SELECT promoter_id"), any(RowMapper.class), eq(token))).thenReturn(List.of(7L));
        when(jdbc.queryForList(startsWith("SELECT request_hash"), eq(token), anyString())).thenReturn(List.of());
        when(referrals.create(eq(7L), anyMap())).thenReturn(Map.of("customerId", 42L, "lockStatus", 2));
    }
    @Test void opaqueTokenDeterminesPartnerAndNewSubmissionSavesReceipt() {
        assertThat(service.submit(token, body).get("customerId")).isEqualTo(42L);
        verify(referrals).create(7L, body);
        verify(referrals, never()).create(eq(999L), anyMap());
        verify(jdbc).update(startsWith("INSERT INTO mkt_customer_referral_submission"), eq(token), eq("request-0123456789"), anyString(), anyString());
    }
    @Test void successfulRetryReturnsReceiptWithoutCreatingAnotherCustomer() {
        final Map<String,Object>[] saved = new Map[]{null};
        when(jdbc.update(startsWith("INSERT INTO mkt_customer_referral_submission"), any(Object[].class))).thenAnswer(i -> {
            saved[0] = Map.of("request_hash", i.getArgument(3), "result_json", i.getArgument(4)); return 1;
        });
        service.submit(token, body);
        when(jdbc.queryForList(startsWith("SELECT request_hash"), eq(token), anyString())).thenReturn(List.of(saved[0]));
        assertThat(service.submit(token, body).get("customerId")).isEqualTo(42);
        verify(referrals, times(1)).create(anyLong(), anyMap());
        body.put("name", "另一个客户");
        assertThatThrownBy(() -> service.submit(token, body)).hasMessageContaining("已处理");
    }
    @Test void malformedDeletedAndFrozenLinksCannotSubmit() {
        assertThatThrownBy(() -> service.submit("7", body)).hasMessageContaining("不可用");
        partner.setStatus(2);
        assertThatThrownBy(() -> service.submit(token, body)).hasMessageContaining("不可用");
        when(partners.selectForUpdate(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(token, body)).hasMessageContaining("不可用");
        verifyNoInteractions(referrals);
    }
    @Test void publicFormDoesNotExposePartnerPhoneOrOtherCustomers() {
        partner.setPhone("13800000007");
        when(warehouses.selectList(any())).thenReturn(List.of());
        assertThat(service.form(token)).containsOnlyKeys("partnerName", "warehouses");
        verifyNoInteractions(referrals);
    }
    @Test void submissionNeedsConsentAndStableId() {
        body.remove("agreed");
        assertThatThrownBy(() -> service.submit(token, body)).hasMessageContaining("授权");
        body.put("agreed", true); body.put("submissionId", "x");
        assertThatThrownBy(() -> service.submit(token, body)).hasMessageContaining("重新打开");
        verifyNoInteractions(referrals);
    }
}
