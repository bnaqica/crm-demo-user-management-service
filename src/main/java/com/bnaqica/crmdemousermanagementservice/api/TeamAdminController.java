package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.request.CreateTeamRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.TicketRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import com.bnaqica.crmdemousermanagementservice.service.TeamAdministrationService;
import com.bnaqica.crmdemousermanagementservice.service.TeamDirectoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for teams. Teams are archived, never deleted. */
@RestController
@RequestMapping("/admin/teams")
@RequiredArgsConstructor
public class TeamAdminController {

    private final TeamDirectoryService directory;
    private final TeamAdministrationService administration;

    @GetMapping
    List<TeamResponse> list() {
        return directory.teams();
    }

    @GetMapping("/{id}")
    TeamResponse get(@PathVariable UUID id) {
        return directory.team(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TeamResponse create(@Valid @RequestBody CreateTeamRequest request) {
        return administration.create(request);
    }

    @PostMapping("/{id}/archive")
    TeamResponse archive(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        return administration.archive(id, request.ticketRef());
    }
}
