package com.bnaqica.crmdemousermanagementservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bundle of catalog permissions an admin can give users of one type. In Keycloak it is a composite client role
 * of {@code crm-api} (INTERNAL) or {@code portal-app} (EXTERNAL) named {@link #name}; see {@code RoleKeycloakSync}.
 * Built-in roles come from the migration and can't be deleted.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@AllArgsConstructor
public class Role {

    @Id
    private UUID id;

    /** Also the Keycloak role name, so it is lower-case letters, digits and hyphens. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignable_to", nullable = false)
    private UserType assignableTo;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
