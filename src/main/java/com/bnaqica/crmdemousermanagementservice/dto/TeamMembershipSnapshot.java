package com.bnaqica.crmdemousermanagementservice.dto;

import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import java.util.UUID;

/** A user's place in one team, for the before and after of an audit event; null when they aren't in it. */
public record TeamMembershipSnapshot(UUID teamId, boolean manager) {

    public static TeamMembershipSnapshot of(TeamMember member) {
        return new TeamMembershipSnapshot(member.getTeamId(), member.isManager());
    }
}
