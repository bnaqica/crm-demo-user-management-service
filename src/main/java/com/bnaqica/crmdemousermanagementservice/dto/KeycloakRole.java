package com.bnaqica.crmdemousermanagementservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Keycloak's RoleRepresentation, as returned by and sent back to the role endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakRole(String id, String name, String description) {

    public KeycloakRole(String id, String name) {
        this(id, name, null);
    }
}
