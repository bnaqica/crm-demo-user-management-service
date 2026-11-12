# Design

## Context

UMS, core-service and the portal publish audit events to `audit-events` through their outboxes; Keycloak publishes to `keycloak-events` through a custom event-listener SPI. See proposal.md.

## Goals / Non-Goals

**Goals:** one writer, append-only storage, search for the Admin UI.

**Non-Goals:** retention rules (deferred; monthly partitions make them easy to add); the Keycloak SPI itself (lives with the Keycloak setup in `local-docker`).

## Decisions

- **Hosted in the UMS** as its own `listener` + `service` + second DataSource, not a separate service. It keeps the deployable count down; it can be split out later because nothing else in the UMS touches the audit DataSource. Alternative: a dedicated audit service — more to run for no current gain.
- **Two connections:** `audit_writer` (INSERT on audit tables only) for the consumer, `audit_reader` (SELECT only) for search. Neither can UPDATE or DELETE; revocation is done in the audit DB's migration.
- **Separate Flyway** location `db/audit-migration` run against `crm_audit_db` by its own Flyway bean, as a migration owner role distinct from the writer.
- **Partitioning:** `audit_record` partitioned by month on `occurred_at`; a job creates next month's partition ahead of time. Dedup via unique `(event_id, occurred_at)` and `ON CONFLICT DO NOTHING`.
- **Hash chain (optional):** column `prev_hash`, off by default behind a property.
- **Kafka error handling:** `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` to `audit-events-dlq`, no retry on deserialization errors.

## Risks / Trade-offs

- [Consumer down → audit gap] → Kafka retains events; Keycloak keeps its own events for 30 days as a fallback.
- [Search over a large table] → bounded time range and indexes on `(occurred_at)`, `(actor_id)`, `(target_id)`, `(ticket_ref)`.
