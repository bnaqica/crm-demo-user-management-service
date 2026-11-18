package com.bnaqica.crmdemousermanagementservice.support;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real PostgreSQL, Kafka, Keycloak (with the test realms from {@code src/test/resources/keycloak}) and Mailpit.
 * Import into {@code @SpringBootTest} classes that talk to Keycloak; Spring caches the context, so the containers
 * start once per test run.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(AuditTestDatabase.class)
public class IntegrationTestContainers {

    static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.1"; // same as local-docker
    static final String[] REALMS = {"crm-admin", "crm-internal", "portal"};
    /** Containers start in seconds on an idle machine, but much slower when several test runs share Docker. */
    static final Duration BUSY_MACHINE = Duration.ofMinutes(10);

    @Bean
    Network testNetwork() {
        return Network.newNetwork();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18")).withStartupTimeout(BUSY_MACHINE);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.2")).withStartupTimeout(BUSY_MACHINE);
    }

    @Bean
    MailpitContainer mailpitContainer(Network testNetwork) {
        return new MailpitContainer(testNetwork);
    }

    @Bean
    KeycloakContainer keycloakContainer(Network testNetwork, MailpitContainer mailpitContainer) {
        mailpitContainer.start(); // realms send email to mailpit:1025
        return new KeycloakContainer(testNetwork);
    }

    @Bean
    DynamicPropertyRegistrar keycloakProperties(KeycloakContainer keycloak) {
        return registry -> {
            registry.add("ums.keycloak.base-url", keycloak::baseUrl);
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak.issuer("crm-admin"));
            registry.add("ums.internal-issuer-uri", () -> keycloak.issuer("crm-internal"));
            registry.add("ums.portal-issuer-uri", () -> keycloak.issuer("portal"));
            registry.add(
                    "spring.security.oauth2.client.provider.crm-internal.token-uri",
                    () -> keycloak.tokenUri("crm-internal"));
            registry.add("spring.security.oauth2.client.provider.portal.token-uri", () -> keycloak.tokenUri("portal"));
            registry.add(
                    "spring.security.oauth2.client.provider.crm-admin.token-uri", () -> keycloak.tokenUri("crm-admin"));
        };
    }

    public static class KeycloakContainer extends GenericContainer<KeycloakContainer> {

        KeycloakContainer(Network network) {
            super(DockerImageName.parse(KEYCLOAK_IMAGE));
            withNetwork(network);
            withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin");
            withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin");
            withEnv("KC_HEALTH_ENABLED", "true");
            for (String realm : REALMS) {
                withCopyFileToContainer(
                        MountableFile.forClasspathResource("keycloak/" + realm + "-realm.json"),
                        "/opt/keycloak/data/import/" + realm + "-realm.json");
            }
            withCommand("start-dev", "--import-realm");
            withExposedPorts(8080, 9000);
            waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(BUSY_MACHINE));
        }

        /** The master realm demands HTTPS from outside the container; tests use its admin over plain HTTP. */
        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            try {
                String kcadm = "/opt/keycloak/bin/kcadm.sh";
                execInContainer(
                        kcadm,
                        "config",
                        "credentials",
                        "--server",
                        "http://localhost:8080",
                        "--realm",
                        "master",
                        "--user",
                        "admin",
                        "--password",
                        "admin");
                execInContainer(kcadm, "update", "realms/master", "-s", "sslRequired=NONE");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }

        public String baseUrl() {
            return "http://" + getHost() + ":" + getMappedPort(8080);
        }

        public String issuer(String realm) {
            return baseUrl() + "/realms/" + realm;
        }

        public String tokenUri(String realm) {
            return issuer(realm) + "/protocol/openid-connect/token";
        }
    }

    public static class MailpitContainer extends GenericContainer<MailpitContainer> {

        MailpitContainer(Network network) {
            super(DockerImageName.parse("axllent/mailpit:v1.27.10"));
            withNetwork(network);
            withNetworkAliases("mailpit");
            withExposedPorts(8025, 1025);
            waitingFor(Wait.forHttp("/readyz").forPort(8025));
        }

        public String apiUrl() {
            return "http://" + getHost() + ":" + getMappedPort(8025) + "/api/v1";
        }
    }
}
