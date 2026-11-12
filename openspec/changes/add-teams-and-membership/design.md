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
