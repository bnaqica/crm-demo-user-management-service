package com.bnaqica.crmdemousermanagementservice.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Migrates the audit database as its owner ({@code db/audit-migration}), then opens the writer and reader pools. */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuditDataSourceProperties.class)
public class AuditDatabaseConfig {

    @Bean
    AuditDatabase auditDatabase(AuditDataSourceProperties properties) {
        Flyway.configure()
                .dataSource(properties.url(), properties.ownerUsername(), properties.ownerPassword())
                .locations("classpath:db/audit-migration")
                .schemas("audit")
                .defaultSchema("audit")
                .placeholders(Map.of("writer", properties.writerUsername(), "reader", properties.readerUsername()))
                .load()
                .migrate();
        HikariDataSource writer =
                pool("audit-writer", properties, properties.writerUsername(), properties.writerPassword());
        HikariDataSource reader =
                pool("audit-reader", properties, properties.readerUsername(), properties.readerPassword());
        return new AuditDatabase(JdbcClient.create(writer), JdbcClient.create(reader), List.of(writer, reader));
    }

    private static HikariDataSource pool(
            String name, AuditDataSourceProperties properties, String username, String password) {
        var pool = new HikariDataSource();
        pool.setPoolName(name);
        pool.setJdbcUrl(properties.url());
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(4);
        return pool;
    }
}
