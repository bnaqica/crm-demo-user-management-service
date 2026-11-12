package com.bnaqica.crmdemousermanagementservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Keycloak's RoleRepresentation, as returned by and sent back to the role endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {}
