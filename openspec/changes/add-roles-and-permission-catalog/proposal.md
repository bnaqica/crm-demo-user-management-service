# Proposal

## Why

CRM APIs are authorized by per-resource `:read` / `:write` permissions held as `crm-api` client roles. Admins must be able to bundle and grant those permissions, but never invent them, and privileged grants (Super Manager, new admins) need a second admin.

## What Changes

- Permission and UI-feature catalog, registered by services at deploy time and read-only for admins.
- Roles: create and edit roles as bundles of catalog permissions with `assignableTo`, owner and description; assign and revoke them on users in the matching realm.
- Built-in roles: Super Manager (all non-sensitive CRM permissions, auto-extended), Team Manager, Team Member, `external-starter`, `external-standard`.
- Two-admin approval for granting Super Manager and creating admin accounts.
- `RoleAssigned` events and audit for every grant and revoke.

## Capabilities

### New Capabilities
- `permission-catalog`: registration and listing of permissions and UI features.
- `roles`: role definitions, assignability and assignment.
- `privileged-grant-approval`: the two-admin approval for privileged grants.

### Modified Capabilities
- `admin-access` (introduced by `add-user-accounts`): adds the admin-account guardrails (admin accounts exclusive, last admin protected, break-glass alert).

## Impact

- New `ums-admin` confidential client in `crm-admin` (view and manage admin users only) so the UMS can enforce the admin guardrails.

- New tables `permission`, `role`, `role_permission`, `grant_request`.
- New endpoint `PUT /internal/catalog/{service}` for services (client-credentials token with scope `ums:catalog`).
- Keycloak `crm-api` and `portal-app` client roles become managed by the UMS.
- Depends on `add-user-accounts`.
