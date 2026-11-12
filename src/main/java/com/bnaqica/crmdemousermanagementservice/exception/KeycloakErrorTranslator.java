package com.bnaqica.crmdemousermanagementservice.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** Turns Keycloak error responses into {@link KeycloakClientException}, so no REST client type leaks upwards. */
public final class KeycloakErrorTranslator {

    private KeycloakErrorTranslator() {}

    public static void translate(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new KeycloakClientException(
                response.getStatusCode().value(),
                "Keycloak " + request.getMethod() + " " + request.getURI().getPath() + " returned "
                        + response.getStatusCode().value() + ": " + body);
    }
}
