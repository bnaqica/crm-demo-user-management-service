package com.bnaqica.crmdemousermanagementservice.listener;

import com.bnaqica.crmdemousermanagementservice.service.AuditIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The central audit consumer: its own consumer group, so it reads every event even though other listeners in this
 * service also read {@code keycloak-events}. Unusable events go to {@code audit-events-dlq}.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    static final String GROUP = "${spring.application.name}-audit";

    private final AuditIngestionService service;

    @KafkaListener(
            topics = "${ums.topics.audit-events}",
            groupId = GROUP,
            containerFactory = "auditListenerContainerFactory")
    void onAuditEvent(String payload) {
        service.ingestAuditEvent(payload);
    }

    @KafkaListener(
            topics = "${ums.topics.keycloak-events}",
            groupId = GROUP,
            containerFactory = "auditListenerContainerFactory")
    void onKeycloakEvent(String payload) {
        service.ingestKeycloakEvent(payload);
    }
}
