# Tasks

## 1. Data model

- [x] 1.1 Add `V6__roles_catalog.sql` (V5 was taken) (permission, role, role_permission, grant_request) seeding built-in roles; verify the migration test

## 2. Permission catalog

- [x] 2.1 Implement `PUT /internal/catalog/{service}` with scope `ums:catalog` and own-service check; verify registration, removal and wrong-service scenarios
- [x] 2.2 Sync catalog entries to Keycloak client roles and verify with Testcontainers Keycloak
- [x] 2.3 Implement the read-only catalog listing for admins; verify the 405 scenario

## 3. Roles

- [x] 3.1 Implement role create and edit as Keycloak composites with the same-realm rule and write-includes-read; verify scenarios
- [x] 3.2 Implement assign and revoke with assignability rules, audit and `RoleAssigned`; verify scenarios
- [x] 3.3 Implement Super Manager auto-extension with the sensitive flag; verify both scenarios

## 4. Privileged grant approval

- [x] 4.1 Route Super Manager assignment and admin creation through grant requests; verify the pending scenario
- [x] 4.2 Implement approve, reject, expiry and listing; verify the self-approval and second-admin scenarios
- [x] 4.3 Add the `ums-admin` client to the `crm-admin` realm import and implement the admin-exclusive, last-admin and break-glass rules; verify each `admin-access` scenario
- [x] 4.4 Document the roles, catalog and approvals APIs in `docs/`

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
