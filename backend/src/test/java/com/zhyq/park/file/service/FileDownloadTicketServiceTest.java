package com.zhyq.park.file.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileDownloadTicketServiceTest {
    @Test
    void ticketOnlyWorksOnceForItsFile() {
        var service = new FileDownloadTicketService();
        String ticket = service.issue(35L);
        assertFalse(service.consume(36L, ticket));
        String valid = service.issue(35L);
        assertTrue(service.consume(35L, valid));
        assertFalse(service.consume(35L, valid));
    }

    @Test
    void missingUnknownAndTamperedTicketsAreRejected() {
        var service = new FileDownloadTicketService();
        assertFalse(service.consume(35L, null));
        assertFalse(service.consume(35L, ""));
        assertFalse(service.consume(35L, "unknown"));
        String ticket = service.issue(35L);
        assertFalse(service.consume(35L, ticket + "x"));
        assertTrue(service.consume(35L, ticket));
        assertNotEquals(service.issue(35L), service.issue(35L));
    }

    @Test
    void ticketsExpireAfterTwoMinutes() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-17T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        var service = new FileDownloadTicketService(clock);
        String beforeExpiry = service.issue(35L);
        String atExpiry = service.issue(35L);
        when(clock.instant()).thenReturn(now.plusSeconds(119));
        assertTrue(service.consume(35L, beforeExpiry));
        when(clock.instant()).thenReturn(now.plusSeconds(120));
        assertFalse(service.consume(35L, atExpiry));
    }
}
