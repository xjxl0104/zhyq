package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WxLoginTicketsTest {
    @Test void ticketBindsToOriginalOpenidAndCannotBeReplayed() {
        WxLoginTickets tickets = new WxLoginTickets();
        String ticket = tickets.issue(new WxSessionClient.Session("owner", "server-only-key"));
        assertThat(ticket).doesNotContain("owner", "server-only-key");
        assertThatThrownBy(() -> tickets.consume(ticket, "attacker")).isInstanceOf(BizException.class);
        assertThat(tickets.consume(ticket, "owner")).isEqualTo("server-only-key");
        assertThatThrownBy(() -> tickets.consume(ticket, "owner")).isInstanceOf(BizException.class);
    }

    @Test void ticketExpiresAfterFiveMinutes() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L, 300_000L);
        WxLoginTickets tickets = new WxLoginTickets(clock);
        String ticket = tickets.issue(new WxSessionClient.Session("owner", "key"));
        assertThatThrownBy(() -> tickets.consume(ticket, "owner")).isInstanceOf(BizException.class);
    }
}
