package com.bnaqica.crmdemousermanagementservice.config;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.util.Map;

/**
 * The Keycloak Admin client for each user type's realm, plus {@code adminRealm} for crm-admin (admin accounts only,
 * as the {@code ums-admin} service account).
 */
public record KeycloakClients(Map<UserType, KeycloakAdminClient> byType, KeycloakAdminClient adminRealm) {

    public KeycloakClients(Map<UserType, KeycloakAdminClient> byType) {
        this(byType, null);
    }

    public KeycloakAdminClient forType(UserType type) {
        return byType.get(type);
    }
}
