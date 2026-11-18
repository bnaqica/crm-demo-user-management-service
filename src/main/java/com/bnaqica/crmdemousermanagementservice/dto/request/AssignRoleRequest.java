package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank String role, @TicketReference String ticketRef) {}
