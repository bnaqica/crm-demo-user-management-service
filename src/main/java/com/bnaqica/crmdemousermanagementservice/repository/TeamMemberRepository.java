package com.bnaqica.crmdemousermanagementservice.repository;

import com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount;
import com.bnaqica.crmdemousermanagementservice.entity.TeamMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMember.Key> {

    List<TeamMember> findByIdUserSub(UUID userSub);

    List<TeamMember> findByIdTeamId(UUID teamId);

    @Query("SELECT new com.bnaqica.crmdemousermanagementservice.dto.TeamMemberCount(m.id.teamId, count(m),"
            + " sum(CASE WHEN m.manager THEN 1L ELSE 0L END)) FROM TeamMember m GROUP BY m.id.teamId")
    List<TeamMemberCount> countByTeam();
}
