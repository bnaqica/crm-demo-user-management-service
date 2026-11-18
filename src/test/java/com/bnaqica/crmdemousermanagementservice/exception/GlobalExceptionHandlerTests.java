package com.bnaqica.crmdemousermanagementservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitIsTooManyRequests() {
        assertThat(handler.handleRateLimit(new RateLimitExceededException()).getStatus())
                .isEqualTo(429);
    }

    @Test
    void refusedPromotionIsAConflict() {
        assertThat(handler.handlePromotionConflict(new PromotionConflictException("x"))
                        .getStatus())
                .isEqualTo(409);
    }
}
