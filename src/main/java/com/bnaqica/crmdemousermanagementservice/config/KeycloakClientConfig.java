package com.bnaqica.crmdemousermanagementservice.config;

import com.bnaqica.crmdemousermanagementservice.client.KeycloakAdminClient;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.exception.KeycloakErrorTranslator;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * One {@link KeycloakAdminClient} per realm, each sending a client-credentials token for that realm's UMS service
 * account ({@code ums-internal} or {@code ums-portal}, registered under {@code spring.security.oauth2.client}).
 * The master realm is never used.
 */
@Configuration
@EnableConfigurationProperties(UmsProperties.class)
public class KeycloakClientConfig {

    private static final Map<UserType, String> REGISTRATIONS =
            Map.of(UserType.INTERNAL, "ums-internal", UserType.EXTERNAL, "ums-portal");

    /** Works outside web requests too (Kafka listeners, scheduled jobs). */
    @Bean
    OAuth2AuthorizedClientManager serviceAccountClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    }

    @Bean
    KeycloakClients keycloakClients(UmsProperties properties, OAuth2AuthorizedClientManager clientManager) {
        return new KeycloakClients(Map.of(
                UserType.INTERNAL, client(properties, clientManager, UserType.INTERNAL),
                UserType.EXTERNAL, client(properties, clientManager, UserType.EXTERNAL)));
    }

    private static KeycloakAdminClient client(
            UmsProperties properties, OAuth2AuthorizedClientManager clientManager, UserType type) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(clientManager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATIONS.get(type));
        // One cached token per service account, whoever triggered the call
        interceptor.setPrincipalResolver(request -> new AnonymousAuthenticationToken(
                "ums", "ums-service-account", AuthorityUtils.createAuthorityList("ROLE_SERVICE")));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.keycloak().baseUrl() + "/admin/realms/" + type.getRealm())
                .requestInterceptor(interceptor)
                .defaultStatusHandler(HttpStatusCode::isError, KeycloakErrorTranslator::translate)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(KeycloakAdminClient.class);
    }
}
