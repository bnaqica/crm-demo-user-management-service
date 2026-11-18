package com.bnaqica.crmdemousermanagementservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's place in a team; {@code manager} marks a manager of that team. */
@Entity
@Table(name = "team_member")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@AllArgsConstructor
public class TeamMember {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean manager;

    public static TeamMember of(UUID teamId, UUID userSub, boolean manager) {
        return new TeamMember(new Key(teamId, userSub), manager);
    }

    public UUID getTeamId() {
        return id.getTeamId();
    }

    public UUID getUserSub() {
        return id.getUserSub();
    }

    /** Value object, so equality by value is right here (unlike on the entity). */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @lombok.EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "team_id")
        private UUID teamId;

        @Column(name = "user_sub")
        private UUID userSub;
    }
}
