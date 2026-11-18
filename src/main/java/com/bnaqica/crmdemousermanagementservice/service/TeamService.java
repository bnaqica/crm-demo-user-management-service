package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.entity.Team;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** CRUD for {@code team}. The only path to its repository. */
public interface TeamService {

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.TeamConflictException if the name is taken */
    Team create(Team team);

    /** @throws com.bnaqica.crmdemousermanagementservice.exception.TeamNotFoundException if there is none */
    Team get(UUID id);

    /**
     * {@link #get}, holding a share lock on the row until the caller's transaction ends, so the team can't be changed
     * (archived) meanwhile. Needs a transaction.
     */
    Team lockForShare(UUID id);

    Team update(Team team);

    /** Every team, archived ones included, by name. */
    List<Team> findAll();

    List<Team> findAll(Collection<UUID> ids);
}
