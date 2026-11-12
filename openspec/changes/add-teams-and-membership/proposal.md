# Proposal

## Why

CRM data access is team-scoped, and the architecture puts teams and membership in the UMS database rather than Keycloak groups. CRM services need a single source to resolve which teams a user belongs to or manages.

## What Changes

- Admin API to create and archive teams, set managers, add and remove members, and set the per-user `multiTeamAllowed` flag; every write needs a ticket reference.
- Teams accept INTERNAL users only.
- Service-to-service endpoint returning a user's team scope (member teams, managed teams, all-teams flag) for CRM services.
- `TeamMembershipChanged` events through the outbox so CRM services can clear cached scope.

## Capabilities

### New Capabilities
- `teams`: teams, managers, membership and the multi-team rule.
- `team-scope`: the read API CRM services use to resolve a user's team scope.

## Impact

- New tables `team`, `team_member` (with a manager flag), and a `multi_team_allowed` column on `user_account`.
- New endpoint `GET /internal/users/{sub}/team-scope` callable with `crm-internal` service tokens holding scope `ums:team-scope`.
- Depends on `add-user-accounts` (user records, outbox, audit publishing).
