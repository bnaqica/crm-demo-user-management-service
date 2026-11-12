package com.bnaqica.crmdemousermanagementservice.listener;

import com.bnaqica.crmdemousermanagementservice.service.KeycloakEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Hands each message on {@code keycloak-events} to {@link KeycloakEventService}. */
@Component
@RequiredArgsConstructor
public class KeycloakEventListener {

    private final KeycloakEventService service;

    @KafkaListener(topics = "${ums.topics.keycloak-events}")
    void on(String payload) {
        service.handle(payload);
    }
}
