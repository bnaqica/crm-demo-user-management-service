package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetTeamManagerRequest(
        @NotNull Boolean manager, @TicketReference String ticketRef) {}
