package com.bnaqica.crmdemousermanagementservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The central audit database ({@code ums.audit.datasource.*}), separate from the UMS database. The owner runs its
 * migrations; the writer may only insert; the reader may only select.
 */
@Validated
@ConfigurationProperties("ums.audit.datasource")
public record AuditDataSourceProperties(
        @NotBlank String url,
        @NotBlank String ownerUsername,
        @NotBlank String ownerPassword,
        @NotBlank String writerUsername,
        @NotBlank String writerPassword,
        @NotBlank String readerUsername,
        @NotBlank String readerPassword) {}
