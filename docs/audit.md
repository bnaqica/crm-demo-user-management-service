# Central audit

The UMS hosts the single audit consumer for the whole CRM demo. It reads `audit-events` (from the UMS, CRM services
and the portal, through their outboxes) and `keycloak-events` (from the Keycloak event-listener SPI), strips secrets,
and stores one record per event in the separate, append-only database `crm_audit_db`. Admins search it through
`GET /admin/audit`.

## Storage

- Database `crm_audit_db`, schema `audit`, table `audit.audit_record`, partitioned by month on `occurred_at` (UTC).
- Three roles (created by `local-docker/postgres`): `audit_owner` owns the schema and runs the migrations in
  `db/audit-migration`; `audit_writer` may only INSERT (and create a missing monthly partition through
  `audit.ensure_partition`); `audit_reader` may only SELECT. Triggers refuse UPDATE, DELETE and TRUNCATE on the
  audit tables for every role, the owner included. The owner could still drop those triggers, so its credentials
  (`AUDIT_DB_OWNER_*`) are used only to run the migrations at startup; running them from a separate job instead would
  keep them out of the UMS entirely.
- The UMS doesn't start while the audit database is unreachable (its migrations run at startup).
- The partition job (`UMS_AUDIT_PARTITION_CRON`, daily at 02:30 by default, ShedLock `audit-partitions`) keeps this
  month's and next month's partitions ready. An insert into a month without a partition creates it too.
- Each event id is stored once, even if it comes back with another time: ids are claimed in
  `audit.audit_event_id` in the same statement as the insert.
- Retention is not implemented yet; dropping a month's partition is how it will be done.

## Record

`eventId`, `occurredAt`, `source` (KEYCLOAK, UMS, …), `realm`, `actorId`, `actorType` (ADMIN, USER, SYSTEM), `action`,
`targetType`, `targetId`, `outcome` (SUCCESS or FAILURE), `ticketRef`, `before` and `after` (JSON), `ipAddress`,
`correlationId`, `reason`.

### From `audit-events`

The common audit schema (see [events](events.md)) is stored as it is. `eventId` (UUID), `occurredAt` (ISO-8601),
`source` and `action` are required.

### From `keycloak-events`

- **User events** `{"id", "time" (epoch ms), "realm", "type", "userId", "error", "details"}`: `action` is the type
  (`LOGIN`, `LOGIN_ERROR`, `UPDATE_PASSWORD`, `UPDATE_TOTP`, `REGISTER`, `USER_DISABLED_BY_*_LOCKOUT`, …), actor and
  target are the user (actorType USER), `ipAddress` comes from `details.ipAddress`, `after` holds the details, and
  `outcome` is FAILURE when `error` is set or the type ends in `_ERROR`.
- **Admin events** `{"id", "time", "realm", "kind": "ADMIN", "operationType", "resourceType", "resourcePath",
  "authDetails": {"userId", "ipAddress"}, "representation" (JSON text), "error"}`: `action` is
  `ADMIN_<resourceType>_<operationType>` (for example `ADMIN_USER_UPDATE`, `ADMIN_REALM_ROLE_MAPPING_CREATE`), the actor is
  `authDetails.userId` (actorType ADMIN), and `after` holds the representation. The target comes from
  `resourcePath`: `users/{id}/…` is USER `{id}` (so role mappings, password resets and sessions of a user are found by
  their id), likewise `groups/`, `clients/`, `roles-by-id/` and `client-scopes/`; otherwise it is the path's last
  segment with the resource type. **The Keycloak SPI must publish admin events in this shape**, with `"kind": "ADMIN"`.
- A Keycloak id that isn't a UUID is turned into a stable UUID, so redelivered events still match.

### No secrets

Before storing, every field whose name contains `password`, `secret`, `token`, `credential` or `otp` is removed at
any depth (so a representation's `credentials` never reach the store), as is key material (`privateKey`), and so
are the user attributes listed in `UMS_AUDIT_SENSITIVE_ATTRIBUTES`: inside `attributes`, and as Keycloak's
`previous_<name>` / `updated_<name>` event details.

### Bad events

An event that isn't JSON, lacks a required field or doesn't fit a column goes to `audit-events-dlq` at once and
doesn't block the events behind it. While the audit database is unreachable the consumer waits instead, retrying
with growing pauses (up to a minute) without limit, so Kafka keeps the events and none is dead-lettered. Other
failures are retried 3 times, then dead-lettered. Only the failure's type is logged, never the payload. **The DLQ
holds the raw, unsanitised events: treat it as sensitive.** An admin event whose `representation` isn't JSON is
stored with `after` `{"representation": "unreadable"}`. The consumer group is `crm-demo-user-management-service-audit`, separate from the service's own
`keycloak-events` listener.

## Search: `GET /admin/audit`

Admins only (a `crm-admin` token with the realm role `admin`).

| Query | |
|---|---|
| `from`, `to` | Required ISO-8601 instants; `from` inclusive, `to` exclusive, at most `UMS_AUDIT_SEARCH_MAX_RANGE` apart (default 90 days). Missing, backwards or too wide: 400 |
| `source`, `realm`, `actorId`, `targetId`, `action`, `outcome`, `ticketRef`, `correlationId` | Exact matches, all optional |
| `page`, `size` | From 0; size 1 to 200, default 50 |

Newest first. Response: `{"content": [record, …], "page": 0, "size": 50, "totalElements": 3, "totalPages": 1}`, each
record with the fields above (`before` and `after` as JSON). `GET /admin/audit/{eventId}` returns one record (404 if
unknown). Nothing creates, changes or deletes records: `POST`, `PUT` and `DELETE` answer 405.
