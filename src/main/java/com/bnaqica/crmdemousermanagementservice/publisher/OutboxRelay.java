package com.bnaqica.crmdemousermanagementservice.publisher;

import com.bnaqica.crmdemousermanagementservice.entity.OutboxEvent;
import com.bnaqica.crmdemousermanagementservice.exception.OutboxPublishException;
import com.bnaqica.crmdemousermanagementservice.service.AlertService;
import com.bnaqica.crmdemousermanagementservice.service.OutboxService;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publishes committed outbox events to Kafka, oldest first, one event per short transaction, so a sent event is
 * marked at once. An event Kafka refuses is counted and ends the run; after 5 failures it is parked with an alert
 * and the events behind it carry on.
 *
 * <p>Delivery is at least once (a crash between sending and marking sends again), and with several instances
 * order across events isn't guaranteed. Consumers drop duplicates by event id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_EVENTS_PER_RUN = 100;

    private final OutboxService outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionOperations transactions;
    private final AlertService alerts;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ums.outbox.relay-interval:1s}")
    public void relay() {
        for (int i = 0; i < MAX_EVENTS_PER_RUN; i++) {
            try {
                Boolean sent = transactions.execute(
                        status -> outbox.lockNextUnpublished().map(this::send).orElse(false));
                if (!Boolean.TRUE.equals(sent)) {
                    return; // nothing left
                }
            } catch (OutboxPublishException ex) {
                recordFailure(ex);
                return;
            }
        }
    }

    private boolean send(OutboxEvent event) {
        try {
            kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException(event.getId(), event.getTopic(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new OutboxPublishException(event.getId(), event.getTopic(), ex);
        }
        outbox.markPublished(event, Instant.now(clock));
        log.debug("Published outbox event {} to {}", event.getId(), event.getTopic());
        return true;
    }

    private void recordFailure(OutboxPublishException failure) {
        log.error(failure.getMessage(), failure.getCause());
        boolean parked = Boolean.TRUE.equals(transactions.execute(
                status -> outbox.recordFailure(failure.getEventId(), String.valueOf(failure.getCause()))));
        if (parked) {
            alerts.raise(
                    "outbox-parked",
                    "Outbox event " + failure.getEventId() + " for " + failure.getTopic()
                            + " was parked after repeated failures");
        }
    }
}
