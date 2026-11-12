# Proposal

## Why

Self-registered portal users start as `external-starter` with minimal features. The CRM decides when they become standard users, and must do so by an API call that users can never make for themselves.

## What Changes

- `POST /internal/users/{id}/promote`, callable only by the `crm-promoter` client with scope `ums:promote`.
- Swaps `external-starter` for `external-standard` and sets status ACTIVE; idempotent, rate-limited, audited with actor SYSTEM, the calling client and a correlation id; publishes `UserPromoted`.
- Self-registered users are recorded as STARTER on first sight; unpromoted starters expire after a configured number of days, and unused invites are cleaned up.

## Capabilities

### New Capabilities
- `user-promotion`: the promote API and its rules.
- `starter-expiry`: recording self-registered starters and expiring unpromoted starters and unused invites.

## Impact

- New endpoint under `/internal/**` with scope `ums:promote`.
- Consumes `REGISTER` events from `keycloak-events` for the `portal` realm.
- Depends on `add-user-accounts` and `add-roles-and-permission-catalog`.
