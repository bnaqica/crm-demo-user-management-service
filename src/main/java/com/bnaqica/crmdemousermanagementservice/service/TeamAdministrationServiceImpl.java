package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.dto.MultiTeamSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.TeamMembershipSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.TeamSnapshot;
import com.bnaqica.crmdemousermanagementservice.dto.event.AuditAction;
import com.bnaqica.crmdemousermanagementservice.dto.request.AddTeamMemberRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.CreateTeamRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.MultiTeamRequest;
import com.bnaqica.crmdemousermanagementservice.dto.request.SetTeamManagerRequest;
import com.bnaqica.crmdemousermanagementservice.dto.response.MultiTeamResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamMemberResponse;
import com.bnaqica.crmdemousermanagementservice.dto.response.TeamResponse;
import com.bnaqica.crmdemousermanagementservice.entity.Team;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.InvalidUserRequestException;
import com.bnaqica.crmdemousermanagementservice.exception.TeamConflictException;
import com.bnaqica.crmdemousermanagementservice.exception.UserNotFoundException;
import com.bnaqica.crmdemousermanagementservice.publisher.AuditEventPublisher;
import com.bnaqica.crmdemousermanagementservice.publisher.UserEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Each change runs in one transaction with its audit event and, for membership, its {@code TeamMembershipChanged}
 * event. Membership changes first lock the user's row, so the multi-team rule holds under concurrent requests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamAdministrationServiceImpl implements TeamAdministrationService {

    /** Teams hold internal users only, so team events are in the crm-internal realm. */
    private static final UserType TEAM_REALM = UserType.INTERNAL;

    private final TeamService teams;
    private final TeamMemberService members;
    private final UserAccountService accounts;
    private final AdminAccounts admins;
    private final UserEventPublisher userEvents;
    private final AuditEventPublisher audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Override
    public TeamResponse create(CreateTeamRequest request) {
        return audit.auditedCreate(
                AuditAction.TEAM_CREATED,
                TEAM_REALM,
                request,
                request.ticketRef(),
                () -> transactions.execute(status -> {
                    Team team = teams.create(Team.builder()
                            .id(UUID.randomUUID())
                            .name(request.name().strip())
                            .createdTicket(request.ticketRef())
                            .createdAt(Instant.now(clock))
                            .build());
                    audit.success(
                            AuditAction.TEAM_CREATED,
                            TEAM_REALM,
                            team.getId(),
                            request.ticketRef(),
                            null,
                            TeamSnapshot.of(team));
                    log.info("Created team {} (ticket {})", team.getId(), request.ticketRef());
                    return TeamMapper.toResponse(team, List.of());
                }));
    }

    @Override
    public TeamResponse archive(UUID teamId, String ticketRef) {
        return audit.audited(
                AuditAction.TEAM_ARCHIVED,
                TEAM_REALM,
                teamId.toString(),
                ticketRef,
                () -> transactions.execute(status -> {
                    Team team = teams.get(teamId);
                    if (team.isArchived()) {
                        throw new TeamConflictException("The team is already archived");
                    }
                    TeamSnapshot before = TeamSnapshot.of(team);
                    team.setArchivedAt(Instant.now(clock));
                    Team archived = teams.update(team);
                    audit.success(
                            AuditAction.TEAM_ARCHIVED,
                            TEAM_REALM,
                            teamId,
                            ticketRef,
                            before,
                            TeamSnapshot.of(archived));
                    log.info("Archived team {} (ticket {})", teamId, ticketRef);
                    return TeamMapper.toResponse(archived, members.ofTeam(teamId));
                }));
    }

    @Override
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request) {
        UUID sub = request.sub();
        return audit.audited(
                AuditAction.TEAM_MEMBER_ADDED,
                TEAM_REALM,
                sub.toString(),
                request.ticketRef(),
                () -> transactions.execute(status -> {
                    UserAccount user = lockedTeamCandidate(sub);
                    openTeam(teamId);
                    if (members.find(teamId, sub).isPresent()) {
                        throw new TeamConflictException(
                                "The user is already in this team"); // before the multi-team rule
                    }
                    if (!user.isMultiTeamAllowed() && !members.ofUser(sub).isEmpty()) {
                        throw new InvalidUserRequestException(
                                "The user is already in a team and isn't allowed in more than one");
                    }
                    TeamMember member = members.add(TeamMember.of(teamId, sub, request.manager()));
                    membershipChanged(AuditAction.TEAM_MEMBER_ADDED, member, request.ticketRef(), null);
                    return TeamMapper.toResponse(member, user);
                }));
    }

    @Override
    public void removeMember(UUID teamId, UUID sub, String ticketRef) {
        audit.audited(AuditAction.TEAM_MEMBER_REMOVED, TEAM_REALM, sub.toString(), ticketRef, () -> {
            transactions.executeWithoutResult(status -> {
                accounts.lockForUpdate(sub);
                TeamMember member = members.get(teamId, sub);
                members.remove(member);
                audit.success(
                        AuditAction.TEAM_MEMBER_REMOVED,
                        TEAM_REALM,
                        sub,
                        ticketRef,
                        TeamMembershipSnapshot.of(member),
                        null);
                userEvents.teamMembershipChanged(sub, teamId, false, false);
                log.info("Removed user {} from team {} (ticket {})", sub, teamId, ticketRef);
            });
            return null;
        });
    }

    @Override
    public TeamMemberResponse setManager(UUID teamId, UUID sub, SetTeamManagerRequest request) {
        boolean manager = request.manager();
        AuditAction action = manager ? AuditAction.TEAM_MANAGER_SET : AuditAction.TEAM_MANAGER_UNSET;
        return audit.audited(
                action,
                TEAM_REALM,
                sub.toString(),
                request.ticketRef(),
                () -> transactions.execute(status -> {
                    UserAccount user = manager ? lockedTeamCandidate(sub) : accounts.lockForUpdate(sub);
                    if (manager) {
                        openTeam(teamId);
                    }
                    TeamMember member = members.get(teamId, sub);
                    if (member.isManager() == manager) {
                        return TeamMapper.toResponse(member, user); // already so; nothing to change or announce
                    }
                    TeamMembershipSnapshot before = TeamMembershipSnapshot.of(member);
                    member.setManager(manager);
                    TeamMember saved = members.update(member);
                    membershipChanged(action, saved, request.ticketRef(), before);
                    return TeamMapper.toResponse(saved, user);
                }));
    }

    @Override
    public MultiTeamResponse setMultiTeamAllowed(UUID sub, MultiTeamRequest request) {
        boolean allowed = request.allowed();
        return audit.audited(
                AuditAction.USER_MULTI_TEAM_CHANGED,
                TEAM_REALM,
                sub.toString(),
                request.ticketRef(),
                () -> transactions.execute(status -> {
                    UserAccount user = accounts.lockForUpdate(sub);
                    if (user.getUserType() != UserType.INTERNAL) {
                        throw new InvalidUserRequestException("Only internal users can be in teams");
                    }
                    if (!allowed && members.ofUser(sub).size() > 1) {
                        throw new TeamConflictException(
                                "The user is in more than one team; remove them from all but one first");
                    }
                    MultiTeamSnapshot before = new MultiTeamSnapshot(user.isMultiTeamAllowed());
                    user.setMultiTeamAllowed(allowed);
                    user.setUpdatedAt(Instant.now(clock));
                    UserAccount saved = accounts.update(user);
                    audit.success(
                            AuditAction.USER_MULTI_TEAM_CHANGED,
                            saved.getUserType(),
                            sub,
                            request.ticketRef(),
                            before,
                            new MultiTeamSnapshot(saved.isMultiTeamAllowed()));
                    log.info("Set multi-team of user {} to {} (ticket {})", sub, allowed, request.ticketRef());
                    return new MultiTeamResponse(sub, saved.isMultiTeamAllowed());
                }));
    }

    /** The user, locked, if they may join a team or manage one: INTERNAL and not DISABLED. */
    private UserAccount lockedTeamCandidate(UUID sub) {
        UserAccount user;
        try {
            user = accounts.lockForUpdate(sub);
        } catch (UserNotFoundException ex) {
            throw admins.notAUser(sub);
        }
        if (user.getUserType() != UserType.INTERNAL) {
            throw new InvalidUserRequestException("Only internal users can be in teams");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new InvalidUserRequestException("A disabled user can't join or manage a team");
        }
        return user;
    }

    /** The team, if it still takes members and managers. */
    private Team openTeam(UUID teamId) {
        Team team = teams.lockForShare(teamId); // an archive now waits for this change to commit
        if (team.isArchived()) {
            throw new TeamConflictException("The team is archived and takes no new members or managers");
        }
        return team;
    }

    private void membershipChanged(
            AuditAction action, TeamMember after, String ticketRef, TeamMembershipSnapshot before) {
        audit.success(action, TEAM_REALM, after.getUserSub(), ticketRef, before, TeamMembershipSnapshot.of(after));
        userEvents.teamMembershipChanged(after.getUserSub(), after.getTeamId(), true, after.isManager());
        log.info("{} for user {} in team {} (ticket {})", action, after.getUserSub(), after.getTeamId(), ticketRef);
    }
}
