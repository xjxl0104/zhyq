package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.service.MktPartnerShipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MpShipmentControllerTest {
    final MktPartnerShipmentService service = mock(MktPartnerShipmentService.class);
    final MpShipmentController controller = new MpShipmentController(service);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void identityAlwaysComesFromAuthenticatedPromoterWhileMemberRemainsAScopeFilter() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mp:4", null, List.of()));
        var expected = Map.<String, Object>of("totalVolume", "123");
        when(service.report(4L, "team", "2026-10-01", "2026-10-31", 7L)).thenReturn(expected);
        assertThat(controller.report("team", "2026-10-01", "2026-10-31", 7L).getData()).isSameAs(expected);
        verify(service).report(4L, "team", "2026-10-01", "2026-10-31", 7L);
    }

    @Test void unauthenticatedAndWarehouseAccountsCannotUsePartnerShipmentIdentity() {
        assertThatThrownBy(() -> controller.report("own", null, null, null)).isInstanceOf(BizException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("warehouse:4", null, List.of()));
        assertThatThrownBy(() -> controller.report("own", null, null, null)).isInstanceOf(BizException.class);
        verifyNoInteractions(service);
    }
}
