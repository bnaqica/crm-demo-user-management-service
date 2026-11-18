package com.bnaqica.crmdemousermanagementservice.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.dto.AuditRecord;
import com.bnaqica.crmdemousermanagementservice.repository.AuditRecordRepository;
import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** audit-search spec: filtered, bounded, newest-first and read-only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class AuditSearchApiTests {

    private static final Instant T = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private AuditRecordRepository records;

    private AdminApi api;
    private String ticket;
    private UUID oldest;
    private UUID middle;
    private UUID newest;

    @BeforeEach
    void setUp() {
        api = new AdminApi(mockMvc, keycloak, mailpit);
        ticket = "OPS-" + (100000 + new java.util.Random().nextInt(899999));
        oldest = store(T.minus(3, ChronoUnit.HOURS), ticket, "admin-a", "SUCCESS");
        newest = store(T.minus(1, ChronoUnit.HOURS), ticket, "admin-b", "FAILURE");
        middle = store(T.minus(2, ChronoUnit.HOURS), ticket, "admin-a", "SUCCESS");
        store(T.minus(2, ChronoUnit.HOURS), "OPS-1", "admin-a", "SUCCESS");
    }

    private UUID store(Instant at, String ticketRef, String actor, String outcome) {
        UUID id = UUID.randomUUID();
        records.insert(AuditRecord.builder()
                .eventId(id)
                .occurredAt(at)
                .source("UMS")
                .realm("crm-internal")
                .actorId(actor)
                .actorType("ADMIN")
                .action("USER_DISABLED")
                .targetType("USER")
                .targetId("u-" + ticketRef)
                .outcome(outcome)
                .ticketRef(ticketRef)
                .after("{\"status\":\"DISABLED\"}")
                .correlationId("corr-" + ticketRef)
                .build());
        return id;
    }

    private String range() {
        return "from=" + T.minus(1, ChronoUnit.DAYS) + "&to=" + T.plus(1, ChronoUnit.DAYS);
    }

    @Test
    void searchByTicketIsNewestFirst() throws Exception {
        api.get("/admin/audit?" + range() + "&ticketRef=" + ticket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].eventId").value(newest.toString()))
                .andExpect(jsonPath("$.content[1].eventId").value(middle.toString()))
                .andExpect(jsonPath("$.content[2].eventId").value(oldest.toString()))
                .andExpect(jsonPath("$.content[0].after.status").value("DISABLED"));
    }

    @Test
    void filtersCombine() throws Exception {
        api.get("/admin/audit?" + range() + "&ticketRef=" + ticket
                        + "&actorId=admin-a&outcome=SUCCESS&source=UMS&realm=crm-internal&action=USER_DISABLED"
                        + "&targetId=u-" + ticket + "&correlationId=corr-" + ticket)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void resultsArePaged() throws Exception {
        api.get("/admin/audit?" + range() + "&ticketRef=" + ticket + "&page=1&size=2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value(oldest.toString()))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void rangeTooWideIsRejected() throws Exception {
        api.get("/admin/audit?from=" + T.minus(730, ChronoUnit.DAYS) + "&to=" + T)
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchWithoutARangeIsRejected() throws Exception {
        api.get("/admin/audit?ticketRef=" + ticket).andExpect(status().isBadRequest());
    }

    @Test
    void backwardsRangeIsRejected() throws Exception {
        api.get("/admin/audit?from=" + T + "&to=" + T.minus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneRecordById() throws Exception {
        api.get("/admin/audit/" + middle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketRef").value(ticket));
    }

    @Test
    void unknownRecordIsNotFound() throws Exception {
        api.get("/admin/audit/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void deleteIsNotAllowed() throws Exception {
        api.delete("/admin/audit/" + middle, Map.of()).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void changeIsNotAllowed() throws Exception {
        api.put("/admin/audit/" + middle, Map.of("outcome", "SUCCESS")).andExpect(status().isMethodNotAllowed());
    }
}
