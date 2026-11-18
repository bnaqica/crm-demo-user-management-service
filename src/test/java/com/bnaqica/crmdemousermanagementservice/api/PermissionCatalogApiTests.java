package com.bnaqica.crmdemousermanagementservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.repository.OutboxEventRepository;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import com.bnaqica.crmdemousermanagementservice.support.KeycloakTestAdmin;
import com.bnaqica.crmdemousermanagementservice.support.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** permission-catalog spec: registration by services, Keycloak client roles, and the read-only admin listing. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class PermissionCatalogApiTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private OutboxEventRepository outbox;

    private AdminApi api;
    private TestTokens tokens;
    private String resource;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        tokens = new TestTokens(keycloak);
        resource = "res" + UUID.randomUUID().toString().substring(0, 8);
    }

    static Map<String, Object> entry(String key, boolean sensitive) {
        return Map.of("key", key, "realm", "crm-internal", "description", "about " + key, "sensitive", sensitive);
    }

    private ResultActions register(String service, String token, List<Map<String, Object>> entries) throws Exception {
        return mockMvc.perform(put("/internal/catalog/" + service)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("permissions", entries))));
    }

    private ResultActions registerAsCoreService(List<Map<String, Object>> entries) throws Exception {
        return register("core-service", tokens.teamScopeClientToken(), entries);
    }

    private String key(String verb) {
        return resource + ":" + verb;
    }

    @Test
    void newPermissionRegistered() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false), entry(key("write"), false)))
                .andExpect(status().isOk());

        api.get("/admin/catalog?service=core-service")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.key == '" + key("read") + "')].realm").value("crm-internal"))
                .andExpect(jsonPath("$[?(@.key == '" + key("write") + "')].deprecated")
                        .value(false));
    }

    @Test
    void registeredPermissionsBecomeCrmApiClientRoles() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false), entry(key("write"), false)))
                .andExpect(status().isOk());

        assertThat(new KeycloakTestAdmin(keycloak).clientRoleNames("crm-internal", "crm-api"))
                .contains(key("read"), key("write"));
    }

    @Test
    void permissionRemovedFromCodeIsDeprecated() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false), entry("ui:" + resource, false)))
                .andExpect(status().isOk());

        registerAsCoreService(List.of(entry("ui:" + resource, false))).andExpect(status().isOk());

        api.get("/admin/catalog?service=core-service")
                .andExpect(jsonPath("$[?(@.key == '" + key("read") + "')].deprecated")
                        .value(true))
                .andExpect(jsonPath("$[?(@.key == 'ui:" + resource + "')].deprecated")
                        .value(false));
    }

    @Test
    void reRegisteredPermissionIsActiveAgain() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false))).andExpect(status().isOk());
        registerAsCoreService(List.of()).andExpect(status().isOk());

        registerAsCoreService(List.of(entry(key("read"), false))).andExpect(status().isOk());

        api.get("/admin/catalog?service=core-service")
                .andExpect(jsonPath("$[?(@.key == '" + key("read") + "')].deprecated")
                        .value(false));
    }

    @Test
    void serviceOverwritingAnotherServiceIsForbidden() throws Exception {
        register("portal-service", tokens.teamScopeClientToken(), List.of(entry(key("read"), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutCatalogScopeIsForbidden() throws Exception {
        String promoter = tokens.clientToken("crm-internal", "crm-promoter", "crm-promoter-local-secret");

        register("crm-promoter", promoter, List.of(entry(key("read"), false))).andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCannotRegister() throws Exception {
        register("core-service", tokens.adminToken(), List.of(entry(key("read"), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedKeyIsRejected() throws Exception {
        registerAsCoreService(List.of(entry("Deals.Read", false))).andExpect(status().isBadRequest());
    }

    @Test
    void unknownRealmIsRejected() throws Exception {
        registerAsCoreService(List.of(Map.of("key", key("read"), "realm", "crm-admin", "description", "x")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCreatingAPermissionIsNotAllowed() throws Exception {
        api.post("/admin/catalog", entry(key("read"), false)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminDeletingAPermissionIsNotAllowed() throws Exception {
        api.delete("/admin/catalog", Map.of()).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void registrationAuditedAsTheCallingService() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false))).andExpect(status().isOk());

        assertThat(outbox.findAll().stream()
                        .filter(event -> event.getTopic().equals("audit-events"))
                        .map(event -> JSON.readTree(event.getPayload()))
                        .filter(event -> event.get("action").asString().equals("CATALOG_REGISTERED"))
                        .filter(event -> event.get("after").toString().contains(key("read"))))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.get("actorType").asString()).isEqualTo("SYSTEM");
                    assertThat(event.get("actorId").asString()).isEqualTo("core-service");
                    assertThat(event.get("targetType").asString()).isEqualTo("CATALOG");
                    assertThat(event.get("targetId").asString()).isEqualTo("core-service");
                });
    }

    @Test
    void newNonSensitivePermissionJoinsSuperManager() throws Exception {
        registerAsCoreService(List.of(entry(key("read"), false))).andExpect(status().isOk());

        assertThat(new KeycloakTestAdmin(keycloak).clientRoleComposites("crm-internal", "crm-api", "super-manager"))
                .contains(key("read"));
        api.get("/admin/roles/super-manager")
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem(key("read"))));
    }

    @Test
    void newSensitivePermissionStaysOutOfSuperManager() throws Exception {
        registerAsCoreService(List.of(entry(key("write"), true))).andExpect(status().isOk());

        assertThat(new KeycloakTestAdmin(keycloak).clientRoleComposites("crm-internal", "crm-api", "super-manager"))
                .doesNotContain(key("write"));
    }

    @Test
    void concurrentRegistrationsBothSucceed() throws Exception {
        List<Map<String, Object>> entries = List.of(entry(key("read"), false), entry(key("write"), false));
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.CompletableFuture<Integer>> calls = java.util.stream.IntStream.range(0, 2)
                .mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return registerAsCoreService(entries)
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }))
                .toList();
        start.countDown();

        assertThat(calls.stream().map(java.util.concurrent.CompletableFuture::join))
                .containsOnly(200);
    }

    @Test
    void permissionTurnedSensitiveLeavesSuperManager() throws Exception {
        registerAsCoreService(List.of(entry(key("write"), false))).andExpect(status().isOk());

        registerAsCoreService(List.of(entry(key("write"), true))).andExpect(status().isOk());

        assertThat(new KeycloakTestAdmin(keycloak).clientRoleComposites("crm-internal", "crm-api", "super-manager"))
                .doesNotContain(key("write"));
        api.get("/admin/roles/super-manager")
                .andExpect(jsonPath("$.permissions")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(key("write")))));
    }

    @Test
    void userTokenCannotRegister() throws Exception {
        register("core-service", tokens.userToken("crm-internal", "test-employee"), List.of(entry(key("read"), false)))
                .andExpect(status().isForbidden());
    }
}
