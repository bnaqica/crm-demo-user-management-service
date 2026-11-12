package com.bnaqica.crmdemousermanagementservice.config;

import com.bnaqica.crmdemousermanagementservice.exception.InvalidKeycloakEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Listener errors are retried 3 times a second apart, then the record goes to {@code <topic>-dlq}. Unreadable
 * records go there at once. Every dead-lettered record is logged first, so failures never disappear silently.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka) {
        var deadLetter = new DeadLetterPublishingRecoverer(
                kafka, (record, ex) -> new TopicPartition(record.topic() + "-dlq", -1));
        var handler = new DefaultErrorHandler(
                (record, ex) -> {
                    log.error(
                            "Sending record {}-{}@{} to {}-dlq",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.topic(),
                            ex);
                    deadLetter.accept(record, ex);
                },
                new FixedBackOff(1000, 3));
        handler.addNotRetryableExceptions(InvalidKeycloakEventException.class, JacksonException.class);
        return handler;
    }
}
