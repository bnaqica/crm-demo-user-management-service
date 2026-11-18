package com.bnaqica.crmdemousermanagementservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A 404 here means the request got past security; this slice has no controllers or actuator. */
@WebMvcTest(useDefaultFilters = false) // no controllers, so this keeps working as the service grows
@Import(SecurityConfig.class)
class SecurityConfigTests {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    SecurityConfigTests(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesUnlistedPathEvenWithToken() throws Exception {
        mockMvc.perform(get("/anything").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void adminPathNeedsAdminRealmRole() throws Exception {
        mockMvc.perform(get("/admin/anything").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void adminPathAcceptsAdminRealmRole() throws Exception {
        mockMvc.perform(get("/admin/anything")
                        .with(jwt().jwt(token -> token.claim("realm_access", Map.of("roles", List.of("admin"))))
                                .authorities(SecurityConfig.realmRoles())))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalPathNeedsServiceAccount() throws Exception {
        mockMvc.perform(get("/internal/users/" + UUID.randomUUID() + "/team-scope")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_ums:team-scope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalPathNeedsTheEndpointScope() throws Exception {
        mockMvc.perform(get("/internal/users/" + UUID.randomUUID() + "/team-scope")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamScopeAcceptsServiceAccountWithScope() throws Exception {
        mockMvc.perform(get("/internal/users/" + UUID.randomUUID() + "/team-scope")
                        .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_SERVICE"),
                                        new SimpleGrantedAuthority("SCOPE_ums:team-scope"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlistedInternalPathIsDenied() throws Exception {
        mockMvc.perform(get("/internal/anything")
                        .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_SERVICE"),
                                        new SimpleGrantedAuthority("SCOPE_ums:team-scope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void promoteNeedsTheCrmPromoterClient() throws Exception {
        mockMvc.perform(post("/internal/users/" + UUID.randomUUID() + "/promote")
                        .with(jwt().jwt(token -> token.claim("azp", "core-service"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_SERVICE"),
                                        new SimpleGrantedAuthority("SCOPE_ums:promote"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void promoteAcceptsCrmPromoterWithTheScope() throws Exception {
        mockMvc.perform(post("/internal/users/" + UUID.randomUUID() + "/promote")
                        .with(jwt().jwt(token -> token.claim("azp", "crm-promoter"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_SERVICE"),
                                        new SimpleGrantedAuthority("SCOPE_ums:promote"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCredentialsTokenIsAService() {
        Jwt token = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("client_id", "core-service")
                .claim("azp", "core-service")
                .claim("scope", "ums:team-scope profile")
                .build();

        assertThat(SecurityConfig.serviceAuthorities().convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SERVICE", "SCOPE_ums:team-scope", "SCOPE_profile");
    }

    @Test
    void clientIdForAnotherClientIsNotAService() {
        Jwt token = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("client_id", "core-service")
                .claim("azp", "crm-app")
                .claim("scope", "ums:team-scope")
                .build();

        assertThat(SecurityConfig.serviceAuthorities().convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_ums:team-scope");
    }

    @Test
    void userTokenIsNotAService() {
        Jwt token = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "user")
                .claim("scope", "ums:team-scope")
                .build();

        assertThat(SecurityConfig.serviceAuthorities().convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_ums:team-scope");
    }

    @Test
    void adminUiOriginMayCallTheAdminApi() throws Exception {
        mockMvc.perform(options("/admin/users")
                        .header("Origin", "http://localhost:5175")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5175"));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/admin/users")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
}
