package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.request.AddTeamMemberRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateTeamRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.MultiTeamRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.SetTeamManagerRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.MultiTeamResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import java.util.UUID;

/** Admin changes to teams, membership, managers and the multi-team flag; each one audited under a ticket. */
public interface TeamAdministrationService {

    TeamResponse create(CreateTeamRequest request);

    TeamResponse archive(UUID teamId, String ticketRef);

    TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request);

    void removeMember(UUID teamId, UUID sub, String ticketRef);

    TeamMemberResponse setManager(UUID teamId, UUID sub, SetTeamManagerRequest request);

    MultiTeamResponse setMultiTeamAllowed(UUID sub, MultiTeamRequest request);
}
