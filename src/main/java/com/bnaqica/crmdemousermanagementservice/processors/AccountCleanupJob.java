package com.bnaqica.crmdemousermanagementservice.processors;

import com.bnaqica.crmdemousermanagementservice.service.AccountCleanup;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the account cleanup on {@code UMS_CLEANUP_CRON}, on one instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCleanupJob {

    private final AccountCleanup cleanup;
    private final Clock clock;

    @Scheduled(cron = "${ums.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(name = "account-cleanup", lockAtLeastFor = "PT1M")
    public void run() {
        Instant now = Instant.now(clock);
        run("starter expiry", () -> cleanup.expireStarters(now));
        run("invite cleanup", () -> cleanup.removeExpiredInvites(now));
    }

    /** One part failing doesn't stop the other. */
    private static void run(String part, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException ex) {
            log.error("The {} job failed; it runs again on the next schedule", part, ex);
        }
    }
}
