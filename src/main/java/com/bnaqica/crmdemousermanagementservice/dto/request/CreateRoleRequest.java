package com.bnaqica.crmdemousermanagementservice.dto.request;

import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param name also the Keycloak role name: lower-case letters, digits and hyphens
 * @param permissions catalog keys of the role's realm; a {@code :write} brings its {@code :read}
 */
public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,99}$")
        String name,

        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 255) String owner,
        @NotNull UserType assignableTo,
        @NotNull List<@NotBlank String> permissions,
        @TicketReference String ticketRef) {}
