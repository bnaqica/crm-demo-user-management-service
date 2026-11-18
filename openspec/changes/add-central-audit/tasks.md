# Tasks

## 1. Audit database

- [x] 1.1 Add `crm_audit_db` with roles `audit_owner`, `audit_writer`, `audit_reader` to `local-docker/postgres/docker_postgres_init.sql` and verify on a fresh container
- [x] 1.2 Configure the second DataSource and Flyway for `db/audit-migration`; add the partitioned `audit_record` table and grants; verify with Testcontainers that the writer can't UPDATE or DELETE

## 2. Ingestion

- [x] 2.1 Implement the `audit-events` and `keycloak-events` listeners with mapping to the common record; verify the UMS and Keycloak scenarios
- [x] 2.2 Implement secret stripping and verify the representation scenario
- [x] 2.3 Implement dedup and the DLQ error handler; verify redelivery and malformed-event scenarios with Kafka Testcontainers
- [x] 2.4 Add the monthly partition job and verify it creates next month's partition with a fixed `Clock`

## 3. Search

- [x] 3.1 Implement `GET /admin/audit` with filters, range limit and pagination; verify every `audit-search` scenario
- [x] 3.2 Document the audit pipeline and search API in `docs/`

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
