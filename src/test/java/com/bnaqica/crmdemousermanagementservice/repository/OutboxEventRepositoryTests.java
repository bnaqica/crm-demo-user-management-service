package com.bnaqica.crmdemousermanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bnaqica.crmdemousermanagementservice.TestcontainersConfiguration;
import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OutboxEventRepositoryTests {

    @Autowired
    private OutboxEventRepository repository;

    private OutboxEvent saved(Instant createdAt, Instant publishedAt, Instant parkedAt) {
        return repository.saveAndFlush(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("t")
                .messageKey("k")
                .payload("{}")
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .parkedAt(parkedAt)
                .build());
    }

    @Test
    void locksOldestUnpublishedEventThatIsNotParked() {
        Instant t = Instant.parse("2026-11-11T10:00:00Z");
        saved(t, t, null); // published
        saved(t.plusSeconds(1), null, t); // parked
        OutboxEvent next = saved(t.plusSeconds(2), null, null);
        saved(t.plusSeconds(3), null, null);

        assertThat(repository.lockNextUnpublished()).map(OutboxEvent::getId).contains(next.getId());
    }
}
