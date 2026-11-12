# Proposal

## Why

The architecture (CRM User Management Architecture, 11 Nov 2026) makes the User Management Service (UMS) the only way admins manage users. Keycloak holds identity in three realms (`crm-admin`, `crm-internal`, `portal`), but nothing yet lets an admin create, invite, disable, unlock or reset users across those realms with a ticket reference and an audit trail.

## What Changes

- Admin-only REST API under `/admin/users` that accepts tokens from the `crm-admin` realm only.
- Create or invite users in `crm-internal` (INTERNAL) or `portal` (EXTERNAL) through the Keycloak Admin REST API, using one service account per realm (`ums-internal`, `ums-portal`).
- Email is the business key and is unique across both realms; the Keycloak `sub` is the id other services store.
- Account status model: `INVITED`, `STARTER`, `ACTIVE`, `DISABLED`, with allowed transitions enforced.
- Update, enable/disable (disable revokes sessions), unlock, force logout, reset MFA, set user type.
- Every write requires a ticket reference and produces an audit record and a domain event (`UserCreated`, `UserDisabled`) through a transactional outbox to Kafka.

## Capabilities

### New Capabilities
- `user-accounts`: lifecycle of internal and external user accounts managed by admins.
- `admin-access`: who may call the admin API (crm-admin admin tokens only). The admin-account guardrails come in `add-roles-and-permission-catalog`.

## Impact

- New dependencies: Spring OAuth2 client (client credentials for the Keycloak Admin REST API through `@HttpExchange`), Spring Kafka.
- New tables in `crm_demo_user_management_service`: `user_account`, `outbox_event`.
- New Kafka topics: `ums-user-events`, `audit-events`.
- Keycloak realm configuration (clients `admin-app`, `ums-internal`, `ums-portal`) must exist in `local-docker/keycloak` as realm-import files.
