# Design

## Context

Keycloak `crm-api` client roles carry CRM permissions in the token; the UMS owns their metadata. See proposal.md.

## Goals / Non-Goals

**Goals:** catalog from code, composite roles in Keycloak, a single approval mechanism reusable for later privileged actions.

**Non-Goals:** the annotation and build-time check in services (owned by core-service `add-crm-authorization`); general approval/override workflows (deferred in the decision log).

## Decisions

- **Permissions are Keycloak client roles; roles are composite client roles.** `crm-api` holds CRM permissions; `portal-app` holds portal permissions. Tokens then carry effective permissions with no extra lookup. Alternative: resolve permissions at request time from the UMS — adds latency and a hard dependency to every request.
- **Catalog sync** is a full-list `PUT` per service, diffed against stored rows; idempotent so every deploy can call it.
- **Super Manager auto-extension** happens in the catalog sync: after storing new non-sensitive CRM permissions, they are added to the Super Manager composite in the same operation.
- **Grant requests** stored in `grant_request(id, type, target, payload jsonb, requested_by, status, decided_by, expires_at)`; applying a request reuses the normal assign path.
- Built-in roles are seeded by a Flyway migration and flagged `builtIn` so they can't be deleted.

## Risks / Trade-offs

- [Many permissions inflate tokens] → permissions are per resource and verb only; revisit with token-exchange if it grows.
- [Deprecated permission still granted] → listed on the catalog screen for admins to clean up; no silent removal.

## Implementation decisions

Recorded while applying the change.

- **Migration number.** `V5` went to `add-teams-and-membership`, so this change's migration is `V6__roles_catalog.sql`
  (task 1.1 says V5).
- **Keycloak layout, matching the services that read tokens.** core-service reads `resource_access.crm-api.roles`;
  the portal service reads the realm roles `external-starter` / `external-standard`. So every permission is a client
  role (`crm-api` for realm `crm-internal`, `portal-app` for `portal`) and every UMS role is a composite client role
  of the same client. The two built-in portal roles stay realm roles for assignment (invites and promotion already
  use them); each is declared in the realm import as a composite of the `portal-app` client role of the same name,
  whose permissions the UMS manages. Role names are therefore Keycloak-safe: lower-case letters, digits, hyphens.
- **Registration body** is core-service's: `PUT /internal/catalog/{service}` with
  `{"permissions": [{"key", "realm" ("crm-internal" or "portal"), "description", "sensitive"}]}`. The caller's
  `client_id` must equal `{service}`, checked in the security rule (403 otherwise).
- **Keycloak after the database.** The sync commits the catalog first, then makes sure every active permission
  exists as a client role and Super Manager holds the non-sensitive CRM ones. Every step is idempotent, so a
  Keycloak failure (502) is repaired by the next registration, which every deploy sends.
- **Service-account roles.** `ums-internal` and `ums-portal` get `manage-clients` (plus `view-clients`) to create
  client roles and composites. That is broader than this needs (it covers every client in the realm);
  fine-grained admin permissions could narrow it later.
- **Roles are addressed by name** (`/admin/roles/{name}`), since the name is unique and is the Keycloak name. Name
  and `assignableTo` are fixed after creation. Roles aren't deleted (the spec asks only for create and edit).
- **Write includes read** is stored: saving `x:write` stores `x:read` too, so the role, its Keycloak composite and
  the response all show it. If `x:read` isn't in the catalog the save is 422.
- **Events.** Assignment publishes `RoleAssigned`, revocation `RoleRevoked` (same fields), both on
  `ums-user-events` keyed by sub. Re-assigning a held role changes and publishes nothing.
- **Grant requests.** Approving claims the request first (row lock, PENDING to APPROVED), then applies the change
  through the normal assign or create path, and puts the request back to PENDING if applying fails; so two admins
  approving at once can't both apply it. The requester may reject their own request. Expiry is `UMS_GRANT_REQUEST_TTL`
  (default 72h), applied when read or decided; no job is needed. `POST /admin/admins` is the admin-creation entry point;
  the new admin gets the same invite email as internal users (password and TOTP).
- **Admin guardrails** use the new `ums-admin` client in `crm-admin` (`manage-users`, `view-users`,
  `query-users`). "Admin accounts are exclusive" is enforced on the sub: assigning roles or teams to a crm-admin
  account is 422; a new admin's email must also be unused in crm-internal and portal. The last-admin check counts
  enabled crm-admin users holding the realm role `admin`; it is serialised within one UMS instance only (two
  instances disabling the last two admins at the same moment could both pass). Disable also ends the admin's
  sessions; `DELETE /admin/admins/{id}` deletes the Keycloak account.
- **Break-glass** is configured by Keycloak user id (`UMS_BREAK_GLASS_ADMIN_ID`, empty = off) and detected from
  crm-admin `LOGIN` events on `keycloak-events`; the audit record has actor `ums` / SYSTEM.
- **Audit actors.** Calls from service accounts on `/internal/**` are audited with actorType SYSTEM and the
  client id (used by catalog registration, and by promotion in `add-external-user-promotion`).
- **Code review (code-reviewer agent).** No Critical findings. Fixed: concurrent registrations (each one now locks
  the Super Manager row first, and Keycloak "already exists" conflicts from another replica are tolerated);
  admins can't remove a non-sensitive CRM permission from Super Manager (422); a permission re-registered as
  sensitive leaves Super Manager; role create and edit now commit first and then sync Keycloak from the committed
  role (no stale restore); a half-created admin account is deleted so the approval can be retried; break-glass
  drops redelivered events and derives the audit event id from the Keycloak event id; a role sync fails clearly
  when a permission's client role is missing in Keycloak.
  Deferred, recorded for the developer: in the `local` profile both requester and approver are `anonymous`, so
  approvals can't be tested there without authentication; an admin can build a custom role as broad as Super Manager
  without approval (only the `super-manager` role is guarded; a product decision); several pending requests for the
  same user or email are allowed (the later approvals fail harmlessly with 409); a permission key stays owned by the
  service that first registered it; changed descriptions don't reach existing Keycloak client roles; two concurrent
  assigns of the same role can publish `RoleAssigned` twice; the 200-or-202 choice for role assignment sits in the
  controller; new `ums.*` settings are read with `@Value` rather than `UmsProperties`.
