package com.bnaqica.crmdemousermanagementservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * A Keycloak login or admin event as published to {@code keycloak-events} by the Keycloak event-listener SPI.
 *
 * @param id unique event id, used to drop duplicates
 * @param time when it happened, in epoch milliseconds
 * @param type Keycloak's event type, such as {@code LOGIN} or {@code USER_DISABLED_BY_TEMPORARY_LOCKOUT}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakEvent(
        String id, long time, String realm, String type, String userId, String error, Map<String, String> details) {}
