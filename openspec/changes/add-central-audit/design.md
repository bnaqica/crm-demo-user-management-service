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

## Implementation decisions

Recorded while applying the change.

- **Connections.** `audit_owner` runs Flyway (`db/audit-migration`, schema `audit`) once at startup and is not kept
  open; `audit_writer` and `audit_reader` get small pools of their own. None of them is a Spring `DataSource` bean, so
  Spring Boot's own DataSource, Flyway and JdbcTemplate stay the UMS database's. Role names come from configuration
  and reach the migration's GRANTs as Flyway placeholders.
- **Partitions** are created by `audit.ensure_partition(date)`, a SECURITY DEFINER function owned by `audit_owner`
  that the writer may execute; the writer never gets DDL rights. The migration creates this and next month's
  partitions; the daily job keeps next month ready; an insert into a month that has none creates it and retries.
  The writer has no rights on the partitions themselves, only INSERT on the parent.
- **Dedup** is `INSERT … ON CONFLICT DO NOTHING` on `(event_id, occurred_at)` (the partition key has to be in the key).
- **Keycloak admin events** have no shape yet (the SPI isn't built), so this change defines it in `docs/audit.md`
  (`"kind": "ADMIN"`, operation, resource, authDetails, representation). All Keycloak user events are stored, a
  superset of the login, failure, lockout, password-reset and MFA events the spec lists.
- **Secrets** are removed by field name at any depth (password, secret, token, credential, otp) plus the configured
  user attributes inside `attributes`.
- **DLQ**: one `audit-events-dlq` for records from both topics, through a container factory of its own; the
  service's existing `keycloak-events` listener keeps its `keycloak-events-dlq`.
- **Hash chain** (optional in the design) is not built; nothing asks for it yet.
- **Search** requires `from` and `to`, newest first, page size up to 200; `GET /admin/audit/{eventId}` exists so that
  DELETE and PUT on a record URL answer 405.
- **Code review (code-reviewer agent).** No Critical findings. Fixed: an event id is stored once even when it comes
  back with another time (`audit.audit_event_id`, V2; lookup by id no longer fails on duplicates; break-glass audit
  ids use their own prefix); admin events on sub-resources (`users/{id}/role-mappings/…`) target the user; changed
  sensitive attributes in Keycloak details (`previous_`/`updated_`) and private keys are stripped; an unreachable
  audit database is waited out instead of dead-lettering real events, and values that don't fit go to the DLQ at once;
  triggers make the tables append-only for the owner too (V2); an unreadable representation is stored as
  "unreadable" instead of rejecting the event, and DLQ logging never includes the payload; the pools are closed;
  missing-partition detection uses the SQLState only.
  Deferred: the owner credentials still live in the UMS to run the migrations at startup (a separate migration job
  would remove them; documented); the writer can create partitions for any month (`ensure_partition` takes any
  date); the three audit services call `AuditRecordRepository` directly rather than through one CRUD service; the
  partition job's own clock reading isn't tested (`AuditPartitions` is, with fixed instants); the DLQ has no replay tool.
