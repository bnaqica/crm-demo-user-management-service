package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.repository.AuditRecordRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Keeps a monthly audit partition ready before records for that month arrive: this month's and next month's (UTC).
 * Creating one that exists does nothing. An insert into a month without a partition creates it as well.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitions {

    private final AuditRecordRepository records;

    public void createAhead(Instant now) {
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        records.ensurePartition(today);
        records.ensurePartition(today.plusMonths(1));
        log.info("Audit partitions ready up to {}", today.plusMonths(1).withDayOfMonth(1));
    }
}
