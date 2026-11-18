package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bnaqica.crmdemousermanagementservice.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

/** user-promotion spec, "Promotion audited and limited": calls above the per-minute limit are refused and alerted. */
class PromotionRateLimiterTests {

    private final AlertService alerts = mock(AlertService.class);
    private final PromotionRateLimiter limiter = new PromotionRateLimiter(alerts, 2);

    @Test
    void callsWithinTheLimitPass() {
        limiter.acquire();

        assertThatCode(limiter::acquire).doesNotThrowAnyException();
        verifyNoInteractions(alerts);
    }

    @Test
    void callsAboveTheLimitAreRefusedAndAlertedOnce() {
        limiter.acquire();
        limiter.acquire();

        assertThatThrownBy(limiter::acquire).isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(limiter::acquire).isInstanceOf(RateLimitExceededException.class);

        verify(alerts, times(1)).raise(eq("promotion-volume"), anyString());
    }
}
