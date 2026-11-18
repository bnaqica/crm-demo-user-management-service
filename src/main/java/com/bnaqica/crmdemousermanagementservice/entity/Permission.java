package com.bnaqica.crmdemousermanagementservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A permission a service enforces, registered by that service: {@code <resource>:read}, {@code <resource>:write} or
 * {@code ui:<feature>}. In Keycloak it is a client role of {@code crm-api} (INTERNAL) or {@code portal-app}
 * (EXTERNAL) with the same name. A permission the service stopped registering is deprecated, not deleted.
 */
@Entity
@Table(name = "permission")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@AllArgsConstructor
public class Permission {

    @Id
    private UUID id;

    @Column(name = "permission_key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType realm;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean sensitive;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    public boolean isDeprecated() {
        return deprecatedAt != null;
    }
}
