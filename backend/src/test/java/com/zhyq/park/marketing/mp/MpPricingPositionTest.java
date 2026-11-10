package com.zhyq.park.marketing.mp;

import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.service.MktCustomerPricingService;
import com.zhyq.park.marketing.service.MktPromoterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MpPricingPositionTest {
    final MktCustomerPricingService pricing = mock(MktCustomerPricingService.class);
    final MktPositionMapper positions = mock(MktPositionMapper.class);
    final MpPricingController controller = new MpPricingController(pricing, positions, mock(MktPromoterService.class));
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void mobileChoicesNeverExposeP4EvenIfUnderlyingRowsIncludeIt() {
        when(positions.selectList(any())).thenReturn(List.of(position("P1"), position("P2"), position("P3"), position("P4")));
        assertThat(controller.positions().getData()).extracting(row -> row.get("code")).containsExactly("P1", "P2", "P3");
    }
    @Test void mobileMutationUsesRestrictedServiceAndAuthenticatedActor() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:4", null, List.of()));
        controller.setTeamPosition(7L, Map.of("code", "P4"));
        verify(pricing).setMobileTeamPosition(4L, 7L, "P4");
        verify(pricing, never()).setTeamPosition(any(), any(), any());
    }
    private MktPosition position(String code) {
        MktPosition p = new MktPosition(); p.setCode(code); p.setName(code); return p;
    }
}
