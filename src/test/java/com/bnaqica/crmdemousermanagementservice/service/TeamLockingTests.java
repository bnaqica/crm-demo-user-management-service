package com.bnaqica.crmdemousermanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bnaqica.crmdemousermanagementservice.support.AdminApi;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.KeycloakContainer;
import com.bnaqica.crmdemousermanagementservice.support.IntegrationTestContainers.MailpitContainer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** An add-member or set-manager in progress holds the team, so an archive can't slip in between check and insert. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestContainers.class)
class TeamLockingTests {

    private static final Duration HOLD = Duration.ofSeconds(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakContainer keycloak;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private TeamService teams;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void archiveWaitsForAMembershipChangeThatHoldsTheTeam() throws Exception {
        AdminApi api = new AdminApi(mockMvc, keycloak, mailpit);
        UUID team = UUID.fromString(api.createTeam("Lock " + UUID.randomUUID()));
        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(tx -> {
            teams.lockForShare(team);
            locked.countDown();
            try {
                Thread.sleep(HOLD.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        locked.await();

        long start = System.nanoTime();
        api.post("/admin/teams/" + team + "/archive", Map.of("ticketRef", "OPS-9"))
                .andExpect(status().isOk());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThan(HOLD.dividedBy(2));
        holder.join();
    }
}
