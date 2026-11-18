package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * At most {@code UMS_PROMOTION_RATE_LIMIT} promote calls a minute on this instance. Going over raises the
 * {@code promotion-volume} alert, at most once a minute.
 */
@Component
public class PromotionRateLimiter {

    private static final long ALERT_EVERY_NANOS = Duration.ofMinutes(1).toNanos();

    private final AlertService alerts;
    private final Bucket bucket;
    private final AtomicLong lastAlert = new AtomicLong(System.nanoTime() - ALERT_EVERY_NANOS);

    public PromotionRateLimiter(
            AlertService alerts, @Value("${ums.promotion.rate-limit-per-minute:60}") int perMinute) {
        this.alerts = alerts;
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }

    /** @throws RateLimitExceededException when the minute's calls are used up */
    public void acquire() {
        if (bucket.tryConsume(1)) {
            return;
        }
        long now = System.nanoTime();
        long last = lastAlert.get();
        if (now - last >= ALERT_EVERY_NANOS && lastAlert.compareAndSet(last, now)) {
            alerts.raise("promotion-volume", "Promote calls are above the per-minute limit; extra calls get 429");
        }
        throw new RateLimitExceededException();
    }
}
