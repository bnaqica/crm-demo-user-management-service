package com.bnaqica.crmdemousermanagementservice.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.entity.UserAccount;
import com.bnaqica.crmdemousermanagementservice.entity.UserStatus;
import com.bnaqica.crmdemousermanagementservice.entity.UserType;
import com.bnaqica.crmdemousermanagementservice.service.UserAccountService;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** user-accounts spec, "Search users". Rows are inserted directly; search reads only the UMS table. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class UserSearchApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private UserAccountService accounts;

    private AdminApi api;
    private String marker;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        marker = "q" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID user(String name, UserType type, UserStatus status) {
        UUID sub = UUID.randomUUID();
        String domain = type == UserType.INTERNAL ? "corp.example" : "acme.example";
        accounts.create(UserAccount.builder()
                .sub(sub)
                .userType(type)
                .email(name + "." + marker + "@" + domain)
                .firstName(name)
                .lastName(marker)
                .status(status)
                .createdAt(Instant.parse("2026-11-11T10:00:00Z"))
                .updatedAt(Instant.parse("2026-11-11T10:00:00Z"))
                .build());
        return sub;
    }

    @Test
    void filterByStatusAcrossBothRealms() throws Exception {
        user("amy", UserType.INTERNAL, UserStatus.DISABLED);
        user("bob", UserType.EXTERNAL, UserStatus.DISABLED);
        user("cat", UserType.INTERNAL, UserStatus.ACTIVE);

        api.get("/admin/users?status=DISABLED&name=" + marker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("DISABLED"))))
                .andExpect(jsonPath("$.content[*].firstName", containsInAnyOrder("amy", "bob")));
    }

    @Test
    void filterByUserTypeAndEmail() throws Exception {
        user("amy", UserType.INTERNAL, UserStatus.ACTIVE);
        user("bob", UserType.EXTERNAL, UserStatus.ACTIVE);

        api.get("/admin/users?userType=EXTERNAL&email=" + marker.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("bob"));
    }

    @Test
    void paginates() throws Exception {
        user("amy", UserType.INTERNAL, UserStatus.ACTIVE);
        user("bob", UserType.INTERNAL, UserStatus.ACTIVE);
        user("cat", UserType.INTERNAL, UserStatus.ACTIVE);

        api.get("/admin/users?name=" + marker + "&page=1&size=2&sort=firstName")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[0].firstName").value("cat"));
    }

    @Test
    void getsOneUser() throws Exception {
        UUID sub = user("amy", UserType.INTERNAL, UserStatus.ACTIVE);

        api.get("/admin/users/" + sub)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("amy"));
        api.get("/admin/users/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }
}
