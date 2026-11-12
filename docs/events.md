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
`USER_LOGGED_OUT`, `USER_MFA_RESET`. Requests rejected before reaching the service (401, 403, 400 validation) are
not here; they are in the request log, `audit.api_audit`.

## Consumed: `keycloak-events`

Published by the Keycloak event-listener SPI (not built yet), one message per Keycloak event:

```json
{"id": "…", "time": 1762855200000, "realm": "crm-internal", "type": "LOGIN", "userId": "…",
 "error": null, "details": {}}
```

- `LOGIN` moves an INVITED user to ACTIVE.
- `USER_DISABLED_BY_TEMPORARY_LOCKOUT` and `USER_DISABLED_BY_PERMANENT_LOCKOUT` feed the mass-lockout alert:
  more than `UMS_LOCKOUT_ALERT_THRESHOLD` accounts (default 5) within `UMS_LOCKOUT_ALERT_WINDOW` (default 5m).
- Other types are ignored. Unreadable messages go to `keycloak-events-dlq` without retries; other failures are
  retried 3 times, then dead-lettered. Every dead-lettered message is logged first.

## Alerts

An alert is an `ERROR` log line with the SLF4J marker `ALERT` plus an increment of the Micrometer counter
`ums.alerts{type}`. Types:

- `mass-lockout`: more accounts than the threshold locked within the window in one realm; the realm then stays quiet
  for one window.
- `outbox-parked`: Kafka refused an outbox event 5 times; it is parked (`outbox_event.parked_at`, with `last_error`)
  and needs someone to look at it. Publishing of other events carries on.
