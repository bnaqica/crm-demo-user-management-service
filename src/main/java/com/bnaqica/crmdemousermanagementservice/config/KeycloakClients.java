package com.bnaqica.crmdemousermanagementservice.config;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import java.util.Map;

/** The Keycloak Admin client for each user type's realm. */
public record KeycloakClients(Map<UserType, KeycloakAdminClient> byType) {

    public KeycloakAdminClient forType(UserType type) {
        return byType.get(type);
    }
}
