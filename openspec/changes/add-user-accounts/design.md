# Design

## Context

The service is a fresh scaffold (Spring Boot 4, Java 25, Postgres schema `crm_demo_user_management_service`, JWT resource server). Keycloak runs from `local-docker/keycloak`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One management path for users in `crm-internal` and `portal`, built on the Keycloak Admin REST API.
- Outbox-based event publishing reused by every later UMS change.

**Non-Goals:**
- Roles, teams, promotion and central audit storage (separate changes).
- Keycloak login flows, password policy and MFA policy themselves: these are realm configuration, delivered as realm-import files in `local-docker/keycloak`.
- Anything acting on `crm-admin` accounts: admin creation, admin-exclusive, last-admin and break-glass guardrails (see `add-roles-and-permission-catalog`, which adds a `ums-admin` service account).

## Decisions

- **Keycloak Admin client per realm.** A declarative `@HttpExchange` interface in `client` (the repo rule for clients; the Keycloak admin client library would also bring Jackson 2 and RESTEasy) with one bean per realm, authenticated by client credentials as `ums-internal` / `ums-portal`. Never the `master` realm. Service accounts get only `manage-users`, `view-users`, `query-groups`, narrowed with fine-grained admin permissions. Alternative: a single master-realm admin — rejected (least privilege).
- **Keycloak errors translated in `exception`.** `KeycloakClientException` maps to 502; a Keycloak duplicate (409) is translated by the service into the same 409 as the UMS's own email clash; nothing above `client` sees Keycloak types.
- **Local `user_account` table** holding `sub`, realm, userType, status, lower-cased email (unique index) and ticket of creation. Keycloak stays the identity source; the table holds UMS-owned status and gives a cross-realm uniqueness check and search. Alternative: query both realms on every check — racy and slow.
- **Status ACTIVE on first login** is detected from Keycloak `LOGIN` events delivered on `keycloak-events` (from the Keycloak event-listener SPI) — consumed by a listener in this service.
- **Transactional outbox.** `outbox_event` table written in the same transaction; a scheduled relay publishes to Kafka one event per short transaction and marks each as sent. An event Kafka refuses is counted; after 5 failures it is parked and the `outbox-parked` alert raised, so it stops blocking the events behind it. With several instances, order across events isn't guaranteed; consumers dedupe by event id. Keycloak calls happen before commit; on failure the transaction rolls back and the Keycloak user is compensated (deleted) if it was created.
- **Ticket reference** is a required `ticketRef` field on every admin write request body, validated `^[A-Z][A-Z0-9]*-\d+$` (so project keys such as `OPS2-12` work).
- **Security.** Issuer `crm-admin`; `JwtAuthenticationConverter` maps realm role `admin` to `ROLE_ADMIN`; `/admin/**` requires it.
- **The scaffold's `audit.api_audit` table stays** as the request log; business audit events go to `audit-events` and are stored centrally (`add-central-audit`).

- **`keycloak-events` contract** (until the Keycloak SPI exists): JSON `{id, time, realm, type, userId, error, details}` per Keycloak event, `type` using Keycloak's names (`LOGIN`, `LOGIN_ERROR`, `USER_DISABLED_BY_TEMPORARY_LOCKOUT`, `USER_DISABLED_BY_PERMANENT_LOCKOUT`, `REGISTER`).
- **Alerts** are an `ERROR` log with marker `ALERT` plus a Micrometer counter `ums.alerts{type}`; monitoring turns them into notifications.

- **Roles are read through the user's available-roles endpoint** (`/users/{id}/role-mappings/realm/available`):
  reading `/roles` needs `view-realm`, which the service accounts deliberately don't have.
- **Outbox has no CRUD controller**: like the audit table, `outbox_event` is infrastructure that is never changed
  through an API, an exception to the "every table has a CRUD controller" rule.
- **Validation failures (400) are not business-audited**: they never reach the service. They are in the request log
  (`audit.api_audit`).
- **Usernames follow the email** in `crm-internal` (`registrationEmailAsUsername`), as in `portal`, so a renamed
  user can neither still sign in as nor block their old address.
- **A profile update that fails after Keycloak changed restores Keycloak's previous profile**, and the email check
  ignores the user's own records, so a retried update isn't refused as a clash with itself.
- **Local and test email** goes to Mailpit (added to `local-docker/keycloak`); tests check invites arrive there.

## Risks / Trade-offs

- [Keycloak and local table drift if someone edits Keycloak directly] → Keycloak admin events on `keycloak-events` update or flag the local record.
- [Compensating delete can fail] → logged as an error naming the Keycloak user id, for an admin to delete by hand.
- [Keycloak changed but the UMS save fails afterwards (disable, enable)] → rare (an optimistic-lock clash, answered
  409); the failure is audited and Keycloak admin events will flag the drift once their consumer exists. Profile
  updates restore Keycloak instead.
- [Invite email sent before the UMS record commits] → if the commit then fails, the Keycloak user is deleted and the
  emailed link is dead; acceptable for an admin-driven, retried action.
- [Service accounts hold `manage-users` for the whole realm] → fine-grained admin permissions to narrow them to
  application roles are not configured yet; a follow-up for the realm configuration.

## Migration Plan

New service; Flyway migrations `V2__user_account.sql`, `V3__outbox_event.sql`. Realm-import JSON for the three realms added to `local-docker/keycloak/realms/`.
