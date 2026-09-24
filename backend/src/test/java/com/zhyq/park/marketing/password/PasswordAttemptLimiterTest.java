package com.zhyq.park.marketing.password;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordAttemptLimiterTest {
    @Test void expiredWindowAndSuccessfulLoginAllowFutureAttempts() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1L);
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(clock);
        limiter.check("mp:user", 1);
        assertThatThrownBy(() -> limiter.check("mp:user", 1)).hasMessageContaining("15 分钟");
        assertThatCode(() -> limiter.check("wh:user", 1)).doesNotThrowAnyException();
        when(clock.millis()).thenReturn(15 * 60_000L + 2);
        assertThatCode(() -> limiter.check("mp:user", 1)).doesNotThrowAnyException();
        limiter.clear("mp:user");
        assertThatCode(() -> limiter.check("mp:user", 1)).doesNotThrowAnyException();
    }
}
