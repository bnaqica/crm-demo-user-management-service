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
