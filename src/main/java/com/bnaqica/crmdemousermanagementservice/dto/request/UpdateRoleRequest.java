package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The name and {@code assignableTo} of a role never change. */
public record UpdateRoleRequest(
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 255) String owner,
        @NotNull List<@NotBlank String> permissions,
        @TicketReference String ticketRef) {}
