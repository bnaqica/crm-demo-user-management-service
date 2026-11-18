package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.AddTeamMemberRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.SetTeamManagerRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.service.TeamAdministrationService;
import com.bnaqica.crmdemousermanagementservice.service.TeamDirectoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for a team's members and managers. */
@RestController
@RequestMapping("/admin/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberAdminController {

    private final TeamDirectoryService directory;
    private final TeamAdministrationService administration;

    @GetMapping
    List<TeamMemberResponse> list(@PathVariable UUID teamId) {
        return directory.members(teamId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TeamMemberResponse add(@PathVariable UUID teamId, @Valid @RequestBody AddTeamMemberRequest request) {
        return administration.addMember(teamId, request);
    }

    @PutMapping("/{sub}")
    TeamMemberResponse setManager(
            @PathVariable UUID teamId, @PathVariable UUID sub, @Valid @RequestBody SetTeamManagerRequest request) {
        return administration.setManager(teamId, sub, request);
    }

    /** Carries the ticket in the body, like every other admin write. */
    @DeleteMapping("/{sub}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID teamId, @PathVariable UUID sub, @Valid @RequestBody TicketRequest request) {
        administration.removeMember(teamId, sub, request.ticketRef());
    }
}
