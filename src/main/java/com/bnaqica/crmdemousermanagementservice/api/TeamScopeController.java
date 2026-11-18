package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.response.TeamScopeResponse;
import com.bnaqica.crmdemousermanagementservice.service.TeamScopeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** For CRM services: a crm-internal client-credentials token with scope {@code ums:team-scope}. */
@RestController
@RequiredArgsConstructor
public class TeamScopeController {

    private final TeamScopeService scopes;

    @GetMapping("/internal/users/{sub}/team-scope")
    TeamScopeResponse scope(@PathVariable UUID sub) {
        return scopes.scope(sub);
    }
}
