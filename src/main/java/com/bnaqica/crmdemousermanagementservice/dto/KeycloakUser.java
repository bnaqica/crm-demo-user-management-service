package com.bnaqica.crmdemousermanagementservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;

/** Keycloak's UserRepresentation, limited to the fields the UMS uses. Null fields are left out, so an update changes only what is set. */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        List<String> requiredActions) {}
