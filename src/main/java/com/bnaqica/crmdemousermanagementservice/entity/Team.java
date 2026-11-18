package com.bnaqica.crmdemousermanagementservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A team of internal users. CRM data access is scoped by team. An archived team keeps its members but takes no new ones. */
@Entity
@Table(name = "team")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@AllArgsConstructor
public class Team {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_ticket", nullable = false)
    private String createdTicket;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    public boolean isArchived() {
        return archivedAt != null;
    }
}
