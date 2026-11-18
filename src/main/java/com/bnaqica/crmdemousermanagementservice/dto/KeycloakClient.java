package com.bnaqica.crmdemousermanagementservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Keycloak's ClientRepresentation: {@code id} is the internal UUID, {@code clientId} the name tokens carry. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakClient(String id, String clientId) {}
