package com.bnaqica.crmdemousermanagementservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnaqica.crmdemousermanagementservice.TestcontainersConfiguration;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequest;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestStatus;
import com.bnaqica.crmdemousermanagementservice.entity.GrantRequestType;
import com.bnaqica.crmdemousermanagementservice.entity.Permission;
import com.bnaqica.crmdemousermanagementservice.entity.Role;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** V6__roles_catalog.sql: permission, role, role_permission and grant_request, with the built-in roles seeded. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RoleCatalogRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private GrantRequestRepository grantRequests;

    private static Permission permission(String key) {
        return Permission.builder()
                .id(UUID.randomUUID())
                .key(key)
                .service("core-service")
                .realm(UserType.INTERNAL)
                .description("d")
                .createdAt(NOW)
                .build();
    }

    @Test
    void builtInRolesAreSeeded() {
        assertThat(roles.findAll())
                .filteredOn(Role::isBuiltIn)
                .extracting(Role::getName)
                .contains("super-manager", "team-manager", "team-member", "external-starter", "external-standard");
        assertThat(roles.findByNameIgnoreCase("external-standard").orElseThrow().getAssignableTo())
                .isEqualTo(UserType.EXTERNAL);
        assertThat(roles.findByNameIgnoreCase("super-manager").orElseThrow().getAssignableTo())
                .isEqualTo(UserType.INTERNAL);
    }

    @Test
    void permissionKeyIsUniquePerRealm() {
        permissions.saveAndFlush(permission("deals:read"));

        assertThatThrownBy(() -> permissions.saveAndFlush(permission("deals:read")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleKeepsItsPermissions() {
        Permission read = permissions.saveAndFlush(permission("notes:read"));
        Role role = roles.saveAndFlush(Role.builder()
                .id(UUID.randomUUID())
                .name("Note reader " + UUID.randomUUID())
                .description("d")
                .owner("ops")
                .assignableTo(UserType.INTERNAL)
                .permissions(Set.of(read))
                .createdAt(NOW)
                .updatedAt(NOW)
                .build());

        assertThat(roles.findById(role.getId()).orElseThrow().getPermissions())
                .extracting(Permission::getKey)
                .containsExactly("notes:read");
    }

    @Test
    void grantRequestStoresItsPayloadAsJson() {
        GrantRequest saved = grantRequests.saveAndFlush(GrantRequest.builder()
                .id(UUID.randomUUID())
                .type(GrantRequestType.SUPER_MANAGER)
                .target(UUID.randomUUID().toString())
                .payload("{\"ticketRef\": \"OPS-3\"}")
                .requestedBy("admin-a")
                .status(GrantRequestStatus.PENDING)
                .ticketRef("OPS-3")
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(3600))
                .build());

        assertThat(grantRequests.findById(saved.getId()).orElseThrow().getPayload())
                .contains("OPS-3");
    }
}
