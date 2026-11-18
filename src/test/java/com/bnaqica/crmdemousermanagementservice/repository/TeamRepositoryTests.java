package com.bnaqica.crmdemousermanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnaqica.crmdemousermanagementservice.TestcontainersConfiguration;
import com.bnaqica.crmdemousermanagementservice.entity.Team;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** V5__team.sql: team, team_member and user_account.multi_team_allowed. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TeamRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Autowired
    private TeamRepository teams;

    @Autowired
    private TeamMemberRepository members;

    @Autowired
    private UserAccountRepository users;

    private static Team team(String name) {
        return Team.builder()
                .id(UUID.randomUUID())
                .name(name)
                .createdTicket("OPS-1")
                .createdAt(NOW)
                .build();
    }

    private UserAccount user() {
        return users.saveAndFlush(UserAccount.builder()
                .sub(UUID.randomUUID())
                .userType(UserType.INTERNAL)
                .email(UUID.randomUUID() + "@corp.example")
                .status(UserStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build());
    }

    @Test
    void teamNamesAreUniqueIgnoringCase() {
        teams.saveAndFlush(team("North Sales"));

        assertThatThrownBy(() -> teams.saveAndFlush(team("north sales")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void memberKeepsManagerFlagAndIsFoundByUser() {
        Team team = teams.saveAndFlush(team("South"));
        UserAccount user = user();

        members.saveAndFlush(TeamMember.of(team.getId(), user.getSub(), true));

        assertThat(members.findByIdUserSub(user.getSub())).singleElement().satisfies(member -> {
            assertThat(member.getId().getTeamId()).isEqualTo(team.getId());
            assertThat(member.isManager()).isTrue();
        });
    }

    @Test
    void memberMustReferenceAnExistingUser() {
        Team team = teams.saveAndFlush(team("East"));

        assertThatThrownBy(() -> members.saveAndFlush(TeamMember.of(team.getId(), UUID.randomUUID(), false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multiTeamIsOffByDefault() {
        assertThat(users.findById(user().getSub()).orElseThrow().isMultiTeamAllowed())
                .isFalse();
    }
}
