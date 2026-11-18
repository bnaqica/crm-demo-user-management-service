package com.bnaqica.crmdemousermanagementservice.support;

import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Calls the admin API as the test admin, with a real crm-admin token. */
public class AdminApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;
    private final MailpitContainer mailpit;
    private final String token;

    public AdminApi(MockMvc mockMvc, KeycloakContainer keycloak, MailpitContainer mailpit) {
        this(mockMvc, keycloak, mailpit, "test-admin");
    }

    /** As another crm-admin user, such as {@code test-admin-2}. */
    public AdminApi(MockMvc mockMvc, KeycloakContainer keycloak, MailpitContainer mailpit, String admin) {
        this.mockMvc = mockMvc;
        this.mailpit = mailpit;
        this.token = new TestTokens(keycloak).userToken("crm-admin", admin);
    }

    /** The test admin's sub, as the audit records it. */
    public String adminSub() throws Exception {
        String payload = token.split("\\.")[1];
        return JSON.readTree(java.util.Base64.getUrlDecoder().decode(payload))
                .get("sub")
                .asString();
    }

    public ResultActions post(String path, Map<String, ?> body) throws Exception {
        return post(path, body, java.util.UUID.randomUUID().toString());
    }

    public ResultActions post(String path, Map<String, ?> body, String correlationId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    public ResultActions put(String path, Map<String, ?> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    public ResultActions delete(String path, Map<String, ?> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    /** Creates a team and returns its id. */
    public String createTeam(String name) throws Exception {
        return field(post("/admin/teams", Map.of("name", name, "ticketRef", "OPS-1")), "id");
    }

    public ResultActions get(String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token));
    }

    /** Creates an internal user and returns its sub. */
    public String createInternal(String email) throws Exception {
        return field(
                post(
                        "/admin/users/internal",
                        Map.of("email", email, "firstName", "Ana", "lastName", "Test", "ticketRef", "OPS-1")),
                "sub");
    }

    public static String field(ResultActions result, String name) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(name).asString();
    }

    public int emailsTo(String address) {
        Map<?, ?> search = RestClient.create()
                .get()
                .uri(mailpit.apiUrl() + "/search?query=to:" + address)
                .retrieve()
                .body(Map.class);
        return ((Number) search.get("messages_count")).intValue();
    }
}
