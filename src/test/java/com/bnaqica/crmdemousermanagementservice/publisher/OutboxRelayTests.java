package com.bnaqica.crmdemousermanagementservice.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.service.AlertService;
import com.bnaqica.crmdemousermanagementservice.service.OutboxService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

/** Relay behaviour when Kafka refuses an event. Kafka and the outbox table are mocked; the rows are tested in OutboxEventRepositoryTests. */
class OutboxRelayTests {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private final OutboxService outbox = mock(OutboxService.class);
    private final AlertService alerts = mock(AlertService.class);
    private final OutboxRelay relay =
            new OutboxRelay(outbox, kafka, TransactionOperations.withoutTransaction(), alerts, Clock.systemUTC());

    private static OutboxEvent event(String key) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("ums-user-events")
                .messageKey(key)
                .payload("{}")
                .createdAt(Instant.now())
                .build();
    }

    private void sendSucceeds(OutboxEvent event) {
        when(kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private void sendFails(OutboxEvent event) {
        when(kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("record too large")));
    }

    @Test
    void sentEventsAreMarkedOneByOne() {
        OutboxEvent first = event("a");
        OutboxEvent second = event("b");
        sendSucceeds(first);
        sendSucceeds(second);
        when(outbox.lockNextUnpublished()).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());

        relay.relay();

        verify(outbox).markPublished(eq(first), any());
        verify(outbox).markPublished(eq(second), any());
    }

    @Test
    void failedEventIsRecordedAndStopsTheRun() {
        OutboxEvent failing = event("a");
        OutboxEvent next = event("b");
        sendFails(failing);
        when(outbox.lockNextUnpublished()).thenReturn(Optional.of(failing), Optional.of(next));
        when(outbox.recordFailure(eq(failing.getId()), anyString())).thenReturn(false);

        relay.relay();

        verify(outbox).recordFailure(eq(failing.getId()), anyString());
        verify(outbox, never()).markPublished(eq(failing), any());
        verify(kafka, never()).send(next.getTopic(), next.getMessageKey(), next.getPayload());
        verify(alerts, never()).raise(anyString(), anyString());
    }

    @Test
    void eventParkedAfterTooManyFailuresRaisesAnAlert() {
        OutboxEvent failing = event("a");
        sendFails(failing);
        when(outbox.lockNextUnpublished()).thenReturn(Optional.of(failing));
        when(outbox.recordFailure(eq(failing.getId()), anyString())).thenReturn(true);

        relay.relay();

        verify(alerts).raise(eq("outbox-parked"), anyString());
    }
}
