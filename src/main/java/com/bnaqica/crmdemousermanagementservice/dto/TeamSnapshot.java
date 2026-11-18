package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.Team;
import java.time.Instant;

/** A team's audited fields, for the before and after of an audit event. */
public record TeamSnapshot(String name, Instant archivedAt) {

    public static TeamSnapshot of(Team team) {
        return new TeamSnapshot(team.getName(), team.getArchivedAt());
    }
}
