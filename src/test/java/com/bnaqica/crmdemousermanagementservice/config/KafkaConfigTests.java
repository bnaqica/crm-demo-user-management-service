package com.bnaqica.crmdemousermanagementservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bnaqica.crmdemousermanagementservice.exception.InvalidAuditEventException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.util.backoff.BackOffExecution;

/** The audit consumer waits out an audit-database outage instead of dead-lettering real events. */
class KafkaConfigTests {

    @Test
    void databaseOutageIsRetriedWithoutLimit() {
        BackOffExecution execution = KafkaConfig.auditBackOff(new TransientDataAccessResourceException("down"))
                .start();

        for (int i = 0; i < 1000; i++) {
            assertThat(execution.nextBackOff()).isNotEqualTo(BackOffExecution.STOP);
        }
    }

    @Test
    void unstorableRecordIsNotWaitedFor() {
        assertThat(KafkaConfig.auditBackOff(new InvalidAuditEventException("bad", null)))
                .isNull();
        assertThat(KafkaConfig.auditBackOff(new DataIntegrityViolationException("too long")))
                .isNull();
    }
}
