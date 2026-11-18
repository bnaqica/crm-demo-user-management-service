package com.bnaqica.crmdemousermanagementservice.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Connections to the audit database: {@code writer} can only insert, {@code reader} can only select. Not exposed as
 * DataSource or JdbcClient beans, so Spring Boot's own DataSource, Flyway and JdbcClient stay the UMS database's.
 * Closing it closes both pools.
 */
public record AuditDatabase(JdbcClient writer, JdbcClient reader, List<HikariDataSource> pools)
        implements AutoCloseable {

    @Override
    public void close() {
        pools.forEach(HikariDataSource::close);
    }
}
