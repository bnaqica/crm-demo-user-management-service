package com.bnaqica.crmdemousermanagementservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Keycloak's CredentialRepresentation; {@code type} is for example {@code password}, {@code otp} or {@code webauthn}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakCredential(String id, String type) {}
