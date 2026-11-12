package com.bnaqica.crmdemousermanagementservice.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code ums.*}.
 *
 * @param keycloak where the Keycloak Admin REST API is
 * @param corporateDomains email domains that belong to employees; internal users must use one, external users mustn't
 * @param inviteLifespan how long an invite email's link works (24 to 72 hours)
 * @param externalRoles portal roles an admin may give an invited external user
 * @param topics Kafka topic names
 * @param lockoutAlert when lockouts of several accounts raise an alert
 */
@Validated
@ConfigurationProperties("ums")
public record UmsProperties(
        Keycloak keycloak,
        List<String> corporateDomains,

        @DefaultValue("72h") @DurationMin(hours = 24) @DurationMax(hours = 72)
        Duration inviteLifespan,

        @DefaultValue({"external-starter", "external-standard"})
        List<String> externalRoles,

        Topics topics,
        @DefaultValue LockoutAlert lockoutAlert) {

    /** @param baseUrl Keycloak's address, for example {@code http://localhost:8180} */
    public record Keycloak(@NotBlank String baseUrl) {}

    public record Topics(
            @NotBlank String keycloakEvents,
            @NotBlank String userEvents,
            @NotBlank String auditEvents) {}

    /** An alert is raised when more than {@code threshold} accounts lock within {@code window}. */
    public record LockoutAlert(
            @DefaultValue("5") int threshold,
            @DefaultValue("5m") Duration window) {}
}
