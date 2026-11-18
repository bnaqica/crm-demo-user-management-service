package com.bnaqica.crmdemousermanagementservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The separate audit database, with the owner, writer and reader roles that local-docker creates. */
@TestConfiguration(proxyBeanMethods = false)
public class AuditTestDatabase {

    public static final String WRITER_PASSWORD = "writer";
    public static final String READER_PASSWORD = "reader";

    @Bean
    AuditPostgres auditPostgres() {
        return new AuditPostgres();
    }

    @Bean
    DynamicPropertyRegistrar auditDatabaseProperties(AuditPostgres audit) {
        return registry -> {
            registry.add("ums.audit.datasource.url", audit::getJdbcUrl);
            registry.add("ums.audit.datasource.owner-username", audit::getUsername);
            registry.add("ums.audit.datasource.owner-password", audit::getPassword);
            registry.add("ums.audit.datasource.writer-username", () -> "audit_writer");
            registry.add("ums.audit.datasource.writer-password", () -> WRITER_PASSWORD);
            registry.add("ums.audit.datasource.reader-username", () -> "audit_reader");
            registry.add("ums.audit.datasource.reader-password", () -> READER_PASSWORD);
        };
    }

    public static class AuditPostgres extends PostgreSQLContainer {

        AuditPostgres() {
            super(DockerImageName.parse("postgres:18"));
            withDatabaseName("crm_audit_db");
            withUsername("audit_owner");
            withPassword("owner");
            withInitScript("audit-db-init.sql");
            withStartupTimeout(IntegrationTestContainers.BUSY_MACHINE);
        }
    }
}
