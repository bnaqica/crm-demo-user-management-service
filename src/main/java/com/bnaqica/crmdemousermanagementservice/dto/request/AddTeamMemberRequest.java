package com.bnaqica.crmdemousermanagementservice.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** @param manager whether the user also manages the team; false when left out */
public record AddTeamMemberRequest(
        @NotNull UUID sub, Boolean manager, @TicketReference String ticketRef) {

    public AddTeamMemberRequest {
        manager = Boolean.TRUE.equals(manager);
    }
}
