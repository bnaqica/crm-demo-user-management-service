package com.bnaqica.crmdemousermanagementservice.service;

import com.bnaqica.crmdemousermanagementservice.config.KeycloakClients;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakClientException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The Keycloak UUID of the client whose roles hold a realm's permissions and UMS roles: {@code crm-api} in
 * crm-internal, {@code portal-app} in portal. Looked up once per realm.
 */
@Component
@RequiredArgsConstructor
public class KeycloakClientIds {

    public static final String CRM_API = "crm-api";
    public static final String PORTAL_APP = "portal-app";

    private final KeycloakClients keycloak;
    private final Map<UserType, String> ids = new ConcurrentHashMap<>();

    /** The client's {@code clientId}, as tokens carry it. */
    public static String permissionClient(UserType realm) {
        return realm == UserType.INTERNAL ? CRM_API : PORTAL_APP;
    }

    public String permissionClientId(UserType realm) {
        return ids.computeIfAbsent(realm, this::lookUp);
    }

    private String lookUp(UserType realm) {
        String clientId = permissionClient(realm);
        return keycloak.forType(realm).clientsByClientId(clientId).stream()
                .filter(found -> clientId.equals(found.clientId()))
                .findFirst()
                .orElseThrow(() -> new KeycloakClientException(404, "Keycloak has no " + clientId + " client"))
                .id();
    }
}
