package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record MultiTeamRequest(
        @NotNull Boolean allowed, @TicketReference String ticketRef) {}
