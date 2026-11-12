package com.bnaqica.crmdemousermanagementservice.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Only tokens from the {@code crm-admin} realm ({@code JWT_ISSUER_URI}) are accepted; a token from any other realm
 * fails issuer validation and gets 401. {@code /admin/**} also needs the realm role {@code admin}. Anything not
 * listed is denied. CSRF protection is off because requests carry a bearer token and there are no sessions or cookies.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String ADMIN_ROLE = "ADMIN";

    @Bean
    @Profile("!local")
    SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
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

    /** No authentication in the {@code local} profile, so the API and Swagger UI work without an identity provider. */
    @Bean
    @Profile("local")
    SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
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
