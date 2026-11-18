package com.bnaqica.crmdemousermanagementservice.support;

import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Gets real access tokens from the test Keycloak with the test-only password-grant client. */
@RequiredArgsConstructor
public class TestTokens {

    public static final String PASSWORD = "Test-password-123";

    private final KeycloakContainer keycloak;

    public String userToken(String realm, String username) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "test-client");
        form.add("username", username);
        form.add("password", PASSWORD);
        Map<?, ?> response = RestClient.create()
                .post()
                .uri(keycloak.tokenUri(realm))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) response.get("access_token");
    }

    /** A client-credentials (service account) token. */
    public String clientToken(String realm, String clientId, String secret) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> response = RestClient.create()
                .post()
                .uri(keycloak.tokenUri(realm))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) response.get("access_token");
    }

    /** core-service's service account in crm-internal, which holds scope {@code ums:team-scope}. */
    public String teamScopeClientToken() {
        return clientToken("crm-internal", "core-service", "core-service-local-secret");
    }

    public String adminToken() {
        return userToken("crm-admin", "test-admin");
    }
}
