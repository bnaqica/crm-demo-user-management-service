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
 * account ({@code ums-internal} or {@code ums-portal}, registered under {@code spring.security.oauth2.client}), and
 * one for crm-admin as {@code ums-admin}, which only reads and creates admin accounts. The master realm is never used.
 */
@Configuration
@EnableConfigurationProperties(UmsProperties.class)
public class KeycloakClientConfig {

    private static final Map<UserType, String> REGISTRATIONS =
            Map.of(UserType.INTERNAL, "ums-internal", UserType.EXTERNAL, "ums-portal");
    static final String ADMIN_REALM = "crm-admin";
    private static final String ADMIN_REGISTRATION = "ums-admin";

    /** Works outside web requests too (Kafka listeners, scheduled jobs). */
    @Bean
    OAuth2AuthorizedClientManager serviceAccountClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    }

    @Bean
    KeycloakClients keycloakClients(UmsProperties properties, OAuth2AuthorizedClientManager clientManager) {
        return new KeycloakClients(
                Map.of(
                        UserType.INTERNAL, client(properties, clientManager, UserType.INTERNAL),
                        UserType.EXTERNAL, client(properties, clientManager, UserType.EXTERNAL)),
                client(properties, clientManager, ADMIN_REALM, ADMIN_REGISTRATION));
    }

    private static KeycloakAdminClient client(
            UmsProperties properties, OAuth2AuthorizedClientManager clientManager, UserType type) {
        return client(properties, clientManager, type.getRealm(), REGISTRATIONS.get(type));
    }

    private static KeycloakAdminClient client(
            UmsProperties properties, OAuth2AuthorizedClientManager clientManager, String realm, String registration) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(clientManager);
        interceptor.setClientRegistrationIdResolver(request -> registration);
        // One cached token per service account, whoever triggered the call
        interceptor.setPrincipalResolver(request -> new AnonymousAuthenticationToken(
                "ums", "ums-service-account", AuthorityUtils.createAuthorityList("ROLE_SERVICE")));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.keycloak().baseUrl() + "/admin/realms/" + realm)
                .requestInterceptor(interceptor)
                .defaultStatusHandler(HttpStatusCode::isError, KeycloakErrorTranslator::translate)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(KeycloakAdminClient.class);
    }
}
