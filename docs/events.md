# Events

All values are JSON strings with no Java type information. Events go through the transactional outbox
(`outbox_event`), so one is published only if its change committed. Delivery is at least once and, with several
instances, not strictly ordered: consumers must drop duplicates by `eventId`.

## Published: `ums-user-events` (key: user `sub`)

```json
{"type": "UserCreated", "eventId": "…", "schemaVersion": 1, "occurredAt": "2026-11-11T10:00:00Z",
 "sub": "…", "userType": "INTERNAL", "email": "ana@corp.example", "status": "INVITED"}
```

```json
{"type": "UserDisabled", "eventId": "…", "schemaVersion": 1, "occurredAt": "…",
 "sub": "…", "userType": "INTERNAL", "email": "ana@corp.example"}
```

```json
{"type": "TeamMembershipChanged", "eventId": "…", "schemaVersion": 1, "occurredAt": "…",
 "sub": "…", "teamId": "…", "member": true, "manager": false}
```

`TeamMembershipChanged` follows every committed membership or manager change; `member` and `manager` describe the
user's place in that team afterwards. CRM services evict their cached team scope for `sub`.

```json
{"type": "RoleAssigned", "eventId": "…", "schemaVersion": 1, "occurredAt": "…",
 "sub": "…", "userType": "INTERNAL", "role": "team-manager"}
```

```json
{"type": "UserPromoted", "eventId": "…", "schemaVersion": 1, "occurredAt": "…",
 "sub": "…", "userType": "EXTERNAL", "email": "pat@acme.example"}
```

`UserPromoted` follows a promotion from `external-starter` to `external-standard`.
`RoleAssigned` and `RoleRevoked` (same fields) follow every committed role assignment or revocation.

## Published: `audit-events` (key: target id, or the event id for a failed create)

One per admin write, whether it succeeded or failed. `before` and `after` hold the user's type, email, names and
status; both are null for actions that change only Keycloak (unlock, logout, MFA reset). `reason` is set on failures.
A failed create has no target id yet: it carries the realm and, as `after`, what was requested.

```json
{"eventId": "…", "schemaVersion": 1, "occurredAt": "…", "source": "UMS", "realm": "crm-internal",
 "actorId": "<admin sub>", "actorType": "ADMIN", "action": "USER_DISABLED", "targetType": "USER",
 "targetId": "<user sub>", "outcome": "SUCCESS", "ticketRef": "OPS-40",
 "before": {"status": "ACTIVE", …}, "after": {"status": "DISABLED", …},
 "ipAddress": "10.0.0.5", "correlationId": "…", "reason": null}
```

Actions: `USER_CREATED`, `USER_INVITED`, `USER_UPDATED`, `USER_DISABLED`, `USER_ENABLED`, `USER_UNLOCKED`,
`USER_LOGGED_OUT`, `USER_MFA_RESET`, `USER_MULTI_TEAM_CHANGED` (before/after `{"multiTeamAllowed": …}`).
Team actions: `TEAM_CREATED` and `TEAM_ARCHIVED` have `targetType` TEAM and before/after `{"name", "archivedAt"}`;
`TEAM_MEMBER_ADDED`, `TEAM_MEMBER_REMOVED`, `TEAM_MANAGER_SET` and `TEAM_MANAGER_UNSET` target the USER, with
before/after `{"teamId", "manager"}` (null when the user isn't in the team). All team audits have realm `crm-internal`.
Role and catalog actions: `ROLE_CREATED` and `ROLE_UPDATED` (target ROLE, by name; before/after
`{"description", "owner", "permissions"}`), `ROLE_ASSIGNED` and `ROLE_REVOKED` (target USER; before/after
`{"roles": [...]}`), `CATALOG_REGISTERED` (target CATALOG, the service; before/after `{"active", "deprecated"}`).
Approvals: `GRANT_REQUESTED`, `GRANT_REQUEST_APPROVED`, `GRANT_REQUEST_REJECTED` (target GRANT_REQUEST; the
snapshot holds `requestedBy` and `decidedBy`, so both admins are recorded). Portal users: `USER_PROMOTED` (actorType SYSTEM, actor `crm-promoter`, the caller's correlation id), and from the
cleanup jobs (actor `ums`) `USER_DISABLED` with after `{"status", "reason": "STARTER_EXPIRED"}` and
`USER_DELETED` with after `{"status", "reason": "INVITE_EXPIRED"}`. Admin accounts (realm `crm-admin`,
target ADMIN): `ADMIN_CREATED`, `ADMIN_DISABLED`, `ADMIN_DELETED`, `BREAK_GLASS_LOGIN`.

`actorType` is `ADMIN` with the admin's sub for admin requests; `SYSTEM` with the calling client id for services
on `/internal/**` (catalog registration); `SYSTEM` with actor `ums` for what the UMS does by itself (break-glass). Requests rejected before reaching the service (401, 403, 400 validation) are
not here; they are in the request log, `audit.api_audit`.

## Consumed by the central audit: `audit-events` and `keycloak-events`

The audit consumer (its own group) stores every event from both topics in the audit database; unusable events go to
`audit-events-dlq`. See [central audit](audit.md), which also defines the admin-event shape the Keycloak SPI must use.

## Consumed: `keycloak-events`

Published by the Keycloak event-listener SPI (not built yet), one message per Keycloak event:

```json
{"id": "…", "time": 1762855200000, "realm": "crm-internal", "type": "LOGIN", "userId": "…",
 "error": null, "details": {}}
```

- `REGISTER` from `portal` records a STARTER user (see [promotion](promotion-api.md)).
- `LOGIN` moves an INVITED user to ACTIVE; in `crm-admin`, a login of the break-glass admin raises an alert.
- `USER_DISABLED_BY_TEMPORARY_LOCKOUT` and `USER_DISABLED_BY_PERMANENT_LOCKOUT` feed the mass-lockout alert:
  more than `UMS_LOCKOUT_ALERT_THRESHOLD` accounts (default 5) within `UMS_LOCKOUT_ALERT_WINDOW` (default 5m).
- Other types are ignored. Unreadable messages go to `keycloak-events-dlq` without retries; other failures are
  retried 3 times, then dead-lettered. Every dead-lettered message is logged first.

## Alerts

An alert is an `ERROR` log line with the SLF4J marker `ALERT` plus an increment of the Micrometer counter
`ums.alerts{type}`. Types:

- `mass-lockout`: more accounts than the threshold locked within the window in one realm; the realm then stays quiet
  for one window.
- `promotion-volume`: promote calls went over `UMS_PROMOTION_RATE_LIMIT` per minute; extra calls get 429.
- `registration-email-clash`: a self-registered portal user's email belongs to another account; not recorded.
- `break-glass-login`: the admin account `UMS_BREAK_GLASS_ADMIN_ID` signed in.
- `outbox-parked`: Kafka refused an outbox event 5 times; it is parked (`outbox_event.parked_at`, with `last_error`)
  and needs someone to look at it. Publishing of other events carries on.
