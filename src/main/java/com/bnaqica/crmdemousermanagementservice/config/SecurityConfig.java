package com.bnaqica.crmdemousermanagementservice.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Two filter chains. {@code /internal/**} is for CRM services: it trusts the {@code crm-internal} issuer
 * ({@code JWT_INTERNAL_ISSUER_URI}) and the {@code crm-admin} one, and each endpoint names the client-credentials
 * scope it needs, so a user or admin token is authenticated but refused with 403. Everything else accepts only
 * {@code crm-admin} tokens ({@code JWT_ISSUER_URI}); a token from any other realm fails issuer validation and gets
 * 401, and {@code /admin/**} also needs the realm role {@code admin}. Anything not listed is denied. CSRF protection
 * is off because requests carry a bearer token and there are no sessions or cookies.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String ADMIN_ROLE = "ADMIN";
    /** Held by client-credentials tokens from crm-internal, the only callers of {@code /internal/**}. */
    static final String SERVICE_ROLE = "SERVICE";

    @Bean
    @Order(1)
    @Profile("!local")
    SecurityFilterChain internalFilterChain(
            HttpSecurity http,
            JwtDecoder adminDecoder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String adminIssuer,
            @Value("${ums.internal-issuer-uri}") String internalIssuer,
            @Value("${ums.portal-issuer-uri}") String portalIssuer)
            throws Exception {
        return http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests -> requests.requestMatchers(HttpMethod.GET, "/internal/users/*/team-scope")
                                .access(serviceWithScope("ums:team-scope"))
                                .requestMatchers(HttpMethod.PUT, "/internal/catalog/{service}")
                                .access(AuthorizationManagers.allOf(serviceWithScope("ums:catalog"), ownService()))
                                .requestMatchers(HttpMethod.POST, "/internal/users/*/promote")
                                .access(AuthorizationManagers.allOf(serviceWithScope("ums:promote"), client(PROMOTER)))
                                .anyRequest()
                                .denyAll())
                .oauth2ResourceServer(server -> server.authenticationManagerResolver(
                        internalCallers(adminDecoder, adminIssuer, internalIssuer, portalIssuer)))
                .build();
    }

    @Bean
    @Profile("!local")
    SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasRole(ADMIN_ROLE)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(realmRoleConverter())))
                .build();
    }

    /**
     * Lets the Admin UI ({@code UMS_CORS_ALLOWED_ORIGINS}, comma-separated) call {@code /admin/**} from the browser.
     * No origin is allowed when the variable is empty.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${ums.cors.allowed-origins:}") List<String> allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/admin/**", config);
        return source;
    }

    /** No authentication in the {@code local} profile, so the API and Swagger UI work without an identity provider. */
    @Bean
    @Profile("local")
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    /** A crm-internal service account whose token carries {@code scope}. */
    static AuthorizationManager<RequestAuthorizationContext> serviceWithScope(String scope) {
        return AuthorizationManagers.allOf(
                AuthorityAuthorizationManager.hasRole(SERVICE_ROLE),
                AuthorityAuthorizationManager.hasAuthority("SCOPE_" + scope));
    }

    /** The client allowed to promote portal users. */
    static final String PROMOTER = "crm-promoter";

    /** The token was issued to {@code clientId} ({@code azp}). */
    static AuthorizationManager<RequestAuthorizationContext> client(String clientId) {
        return (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwt
                        && clientId.equals(jwt.getToken().getClaimAsString("azp")));
    }

    /** The {@code {service}} in the path is the calling client: a service changes only its own entries. */
    static AuthorizationManager<RequestAuthorizationContext> ownService() {
        return (authentication, context) -> new AuthorizationDecision(authentication.get()
                        instanceof JwtAuthenticationToken jwt
                && context.getVariables().get("service").equals(jwt.getToken().getClaimAsString("client_id")));
    }

    /**
     * Picks the decoder by the token's issuer. Admin and portal tokens get no authorities here, so they pass
     * authentication and are refused by every rule (403, as the specs want for user tokens). The crm-internal decoder is built on first use, like Spring Boot's own.
     */
    private static AuthenticationManagerResolver<HttpServletRequest> internalCallers(
            JwtDecoder adminDecoder, String adminIssuer, String internalIssuer, String portalIssuer) {
        var admin = new JwtAuthenticationProvider(adminDecoder);
        admin.setJwtAuthenticationConverter(noAuthorities());
        var internal = new JwtAuthenticationProvider(
                new SupplierJwtDecoder(() -> JwtDecoders.fromIssuerLocation(internalIssuer)));
        internal.setJwtAuthenticationConverter(serviceConverter());
        var portal = new JwtAuthenticationProvider(
                new SupplierJwtDecoder(() -> JwtDecoders.fromIssuerLocation(portalIssuer)));
        portal.setJwtAuthenticationConverter(noAuthorities());
        Map<String, AuthenticationManager> managers = Map.of(
                adminIssuer, admin::authenticate,
                internalIssuer, internal::authenticate,
                portalIssuer, portal::authenticate);
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static JwtAuthenticationConverter noAuthorities() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
        return converter;
    }

    static JwtAuthenticationConverter serviceConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(serviceAuthorities());
        return converter;
    }

    /**
     * {@code SCOPE_<scope>} for each token scope, plus {@code ROLE_SERVICE} when the token is a client-credentials
     * one: Keycloak's {@code service_account} client scope puts {@code client_id} only in those, and it must name
     * the client the token was issued to ({@code azp}).
     */
    static Converter<Jwt, Collection<GrantedAuthority>> serviceAuthorities() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            String clientId = jwt.getClaimAsString("client_id");
            if (clientId != null && clientId.equals(jwt.getClaimAsString("azp"))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + SERVICE_ROLE));
            }
            return authorities;
        };
    }

    /** Keycloak realm roles ({@code realm_access.roles}) become {@code ROLE_<NAME>} authorities. */
    static JwtAuthenticationConverter realmRoleConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRoles());
        return converter;
    }

    static Converter<Jwt, Collection<GrantedAuthority>> realmRoles() {
        return new RealmRoles();
    }

    private static final class RealmRoles implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()))
                    .toList();
        }
    }
}
