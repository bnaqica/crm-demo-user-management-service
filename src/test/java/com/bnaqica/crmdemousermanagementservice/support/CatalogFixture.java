package com.bnaqica.crmdemousermanagementservice.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Registers catalog permissions as core-service would, so tests can build roles from them. */
public class CatalogFixture {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;
    private final TestTokens tokens;

    public CatalogFixture(MockMvc mockMvc, KeycloakContainer keycloak) {
        this.mockMvc = mockMvc;
        this.tokens = new TestTokens(keycloak);
    }

    /** Replaces core-service's catalog with these crm-internal keys; see {@link #register}. */
    public void registerCrm(String... keys) throws Exception {
        register(java.util.Arrays.stream(keys)
                .map(key -> Map.of(key, "crm-internal"))
                .toList());
    }

    /**
     * Replaces core-service's whole catalog with these {key: realm} entries. A test that needs permissions registers
     * all of them in one call, because a registration deprecates whatever it leaves out.
     */
    public void register(List<Map<String, String>> keysAndRealms) throws Exception {
        List<Map<String, Object>> entries = keysAndRealms.stream()
                .flatMap(entry -> entry.entrySet().stream())
                .map(entry -> Map.<String, Object>of(
                        "key", entry.getKey(), "realm", entry.getValue(), "description", entry.getKey()))
                .toList();
        mockMvc.perform(put("/internal/catalog/core-service")
                        .header("Authorization", "Bearer " + tokens.teamScopeClientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("permissions", entries))))
                .andExpect(status().isOk());
    }
}
