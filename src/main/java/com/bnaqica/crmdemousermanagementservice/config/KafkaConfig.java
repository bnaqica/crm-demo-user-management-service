package com.bnaqica.crmdemousermanagementservice.config;

import com.bnaqica.crmdemousermanagementservice.exception.InvalidAuditEventException;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidKeycloakEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Listener errors are retried 3 times a second apart, then the record goes to {@code <topic>-dlq}. Unreadable
 * records go there at once. Every dead-lettered record is logged first, so failures never disappear silently.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    static final String AUDIT_DLQ = "audit-events-dlq";

    /**
     * The audit database being unreachable is waited out, retrying with growing pauses up to a minute and without
     * limit, so Kafka keeps the events instead of dead-lettering real ones. Null: the handler's default.
     */
    static BackOff auditBackOff(Exception ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof DataAccessResourceFailureException) {
                var backOff = new ExponentialBackOff(1000, 2);
                backOff.setMaxInterval(60_000);
                return backOff;
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

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

    /**
     * For the central audit consumer: as above, but every failed record, from either topic, goes to
     * {@code audit-events-dlq}, and a record that can't be stored as it is goes there at once.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> auditListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ObjectProvider<ConsumerFactory<Object, Object>> consumerFactory,
            KafkaOperations<?, ?> kafka) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory.getObject());
        var deadLetter = new DeadLetterPublishingRecoverer(kafka, (record, ex) -> new TopicPartition(AUDIT_DLQ, -1));
        var handler = new DefaultErrorHandler(
                (record, ex) -> {
                    // Only the exception's type: its message may quote the payload, which can hold secrets
                    log.error(
                            "Sending audit record {}-{}@{} to {} after {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            AUDIT_DLQ,
                            rootCause(ex).getClass().getName());
                    deadLetter.accept(record, ex);
                },
                new FixedBackOff(1000, 3));
        handler.addNotRetryableExceptions(
                InvalidAuditEventException.class, JacksonException.class, DataIntegrityViolationException.class);
        handler.setBackOffFunction((record, ex) -> auditBackOff(ex));
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
