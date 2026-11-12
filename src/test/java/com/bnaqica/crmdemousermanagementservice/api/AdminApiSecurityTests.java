package com.bnaqica.crmdemousermanagementservice.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** admin-access spec: only crm-admin tokens with the admin role reach /admin/**. Uses real Keycloak tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class AdminApiSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    private TestTokens tokens;

    @BeforeEach
    void setUp() {
        tokens = new TestTokens(keycloak);
    }

    @Test
    void crmInternalTokenIsRejected() throws Exception {
        String token = tokens.userToken("crm-internal", "test-employee");

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void portalTokenIsRejected() throws Exception {
        String token = tokens.userToken("portal", "test-portal@acme.example");

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crmAdminTokenWithoutAdminRoleIsForbidden() throws Exception {
        String token = tokens.userToken("crm-admin", "test-not-admin");

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenIsProcessed() throws Exception {
        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + tokens.adminToken()))
                .andExpect(status().isOk());
    }
}
