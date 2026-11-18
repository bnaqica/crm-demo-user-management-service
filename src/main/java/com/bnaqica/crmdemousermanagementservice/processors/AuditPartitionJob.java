package com.bnaqica.crmdemousermanagementservice.processors;

import com.bnaqica.crmdemousermanagementservice.service.AuditPartitions;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates next month's audit partition daily, so it exists well before the month starts. */
@Component
@RequiredArgsConstructor
public class AuditPartitionJob {

    private final AuditPartitions partitions;
    private final Clock clock;

    @Scheduled(cron = "${ums.audit.partition-cron:0 30 2 * * *}")
    @SchedulerLock(name = "audit-partitions", lockAtLeastFor = "PT1M")
    public void run() {
        partitions.createAhead(Instant.now(clock));
    }
}
