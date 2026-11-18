# Design

## Context

Builds on `add-user-accounts` (user records, outbox, audit). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:** UMS database as the single source of truth for teams; cheap reads for CRM services.

**Non-Goals:** Cross-team sharing, delegation or per-record exceptions (would move to OpenFGA later). Manager-submitted requests: managers raise tickets outside the system.

## Decisions

- **Tables over Keycloak groups.** `team(id, name unique, archived_at)` and `team_member(team_id, user_sub, manager boolean)`, plus `user_account.multi_team_allowed`. Keeps tokens small and lets the multi-team rule be enforced transactionally. Alternative: Keycloak groups — rejected in the design review (rules are awkward to enforce, tokens grow).
- **Multi-team check** runs inside the membership transaction with a `SELECT ... FOR UPDATE` on the user row, so two concurrent adds can't both pass.
- **Super Manager** is a `crm-api` client role assigned through the roles change; the scope endpoint reads it from Keycloak through the realm client and caches it briefly.
- **Caller authentication** for `/internal/**`: a second JWT decoder for the `crm-internal` issuer, selected by issuer, requiring `SCOPE_ums:team-scope`. `/admin/**` keeps the `crm-admin` issuer only.
- **Events** reuse the outbox; CRM services evict their cache entry on `TeamMembershipChanged`.

## Risks / Trade-offs

- [CRM cache serves stale scope if an event is lost] → CRM caches have a short TTL (5 min) as a backstop, and writes re-check live scope.

## Implementation decisions

Recorded while applying the change.

- **Migration number.** `V4__outbox_event_failures.sql` already existed from `add-user-accounts`, so the team
  migration is `V5__team.sql` (task 1.1 still says V4). `add-roles-and-permission-catalog` therefore moves to `V6`.
- **API shape.** `POST /admin/teams`, `GET /admin/teams[/{id}]`, `POST /admin/teams/{id}/archive`;
  `GET|POST /admin/teams/{id}/members`, `PUT /admin/teams/{id}/members/{sub}` (manager flag),
  `DELETE /admin/teams/{id}/members/{sub}` with the ticket in a JSON body like every other admin write;
  `PUT /admin/users/{sub}/multi-team`. Team names are unique ignoring case.
- **Archived teams** keep their members, still count towards the multi-team rule and still appear in team scope
  (the spec keeps their history and blocks only new members). They refuse new members and new managers (409);
  removing a member or unsetting a manager still works so admins can empty them.
- **Status codes not named by the spec:** adding someone already in the team, archiving twice → 409; a sub or
  team that doesn't exist, or a member that isn't in the team → 404; a DISABLED or EXTERNAL user made member or
  manager → 422; setting `multiTeamAllowed` on an EXTERNAL user → 422.
- **Setting the manager flag to its current value** is a no-op: 200 with the current state, no audit event and no
  `TeamMembershipChanged` (the request itself is still in `audit.api_audit`).
- **Audit targets.** `TEAM_CREATED` and `TEAM_ARCHIVED` target the TEAM; membership and manager actions
  (`TEAM_MEMBER_ADDED`, `TEAM_MEMBER_REMOVED`, `TEAM_MANAGER_SET`, `TEAM_MANAGER_UNSET`) and
  `USER_MULTI_TEAM_CHANGED` target the USER, with `{teamId, manager}` (or null) as before/after. The target type now
  comes from `AuditAction`. All team audits carry realm `crm-internal`.
- **Lock order.** Membership changes lock the user row (`SELECT ... FOR UPDATE`) before reading the team, so the
  multi-team check and the flag change serialise per user.
- **Scope callers.** `/internal/**` has its own filter chain that trusts both the `crm-internal` issuer
  (`JWT_INTERNAL_ISSUER_URI`) and the `crm-admin` issuer, so an admin token there is authenticated and then
  refused with 403 as the spec requires; a portal token gets 401. A caller is a service only when its token has
  Keycloak's `client_id` claim (client-credentials tokens only); the scope check is `SCOPE_ums:team-scope`.
  `/admin/**` still accepts only `crm-admin` tokens.
- **Super Manager** is the `crm-api` client role `super-manager` (effective, so composites count), read with
  `GET /users/{id}/role-mappings/clients/{crm-api}/composite`; the answer is cached per user for
  `UMS_SUPER_MANAGER_CACHE_TTL` (default 1m). The `ums-internal` service account gets `view-clients` to look up
  the `crm-api` client id.
- **`memberTeams`** lists every team the user belongs to, managed ones included; `managedTeams` is the subset they
  manage. Only INTERNAL users have a scope; others get 404.
- **Admin UI contract (requested by the Admin UI session).** Response shapes the spec leaves open follow the UI:
  users carry `multiTeamAllowed`; the team list carries `memberCount` and `managerCount`; members carry `email`,
  `firstName`, `lastName`; `GET /admin/users/{sub}/teams` lists a user's teams. These reads go through
  `TeamDirectoryService`, so the team and member CRUD services don't depend on each other.
- **CORS** for `/admin/**` from `UMS_CORS_ALLOWED_ORIGINS` (none by default, `http://localhost:5175` in `local`),
  methods GET/POST/PUT/DELETE, headers Authorization, Content-Type, X-Correlation-Id. Not in the spec; added because
  the Admin UI can't call the API without it. A `locked` flag on users was asked for but isn't in any spec, so it is
  left out and reported.
- **Code review (code-reviewer agent).** No Critical findings. Fixed: an archive racing an add-member or
  set-manager (membership changes now hold the team `FOR SHARE`, so the archive's UPDATE waits); missing tests for
  the no-op manager change, archived-team rules, EXTERNAL multi-team flag, remove/manager events, the archive audit
  and the Super Manager cache; failed team audits now carry the realm; service tokens must have
  `client_id == azp`; the concurrent-change 409 message no longer says "account"; README optional variables.
  Deferred (minor): the cached `crm-api` client UUID survives a Keycloak realm re-import until the UMS restarts
  (team-scope then answers 502 until restart).
