# Tasks

## 1. Setup

- [x] 1.1 Add Spring Kafka and the Spring OAuth2 client to `build.gradle` and verify `./gradlew build` passes
- [x] 1.2 Configure the resource server for the `crm-admin` issuer with `ROLE_ADMIN` from realm roles; verify with a `@WebMvcTest` that a `crm-internal` token gets 401 and an admin token passes
- [x] 1.3 Add realm-import files for `crm-admin`, `crm-internal` and `portal` (clients `admin-app`, `crm-app`, `crm-api`, `ums-internal`, `portal-app`, `ums-portal`, `crm-promoter`, and `core-service` with scopes `ums:team-scope` and `ums:catalog`) to `local-docker/keycloak/realms/` and verify each realm imports on Keycloak start

## 2. Keycloak client

- [x] 2.1 Implement the per-realm Keycloak client interface with error translation in `exception`; verify with a Testcontainers Keycloak test that create, disable, logout and attack-detection calls work
- [x] 2.2 Verify the service accounts only hold `manage-users`, `view-users`, `query-groups` with a test that a realm-settings call is forbidden

## 3. Accounts and status

- [x] 3.1 Add `V2__user_account.sql` with a unique lower-case email index and verify the migration runs in the Testcontainers test
- [x] 3.2 Implement create-internal and invite-external with userType and corporate-domain checks; verify each `user-accounts` scenario with service and controller tests
- [x] 3.3 Implement the status transition rules and verify every allowed and rejected transition in a unit test
- [x] 3.4 Implement disable (with session revocation), enable, unlock, force logout, MFA reset and profile update; verify each scenario
- [x] 3.5 Implement the paginated user search across both realms and verify the filter scenarios
- [x] 3.6 Consume `keycloak-events` to move INVITED users to ACTIVE on first login and to count lockouts for the mass-lockout alert; verify with listener tests including a duplicate event

## 4. Guardrails, events and audit

- [x] 4.1 Add `V3__outbox_event.sql` and the outbox relay publishing `UserCreated` / `UserDisabled` to `ums-user-events`; verify with a Kafka Testcontainers test and a rollback test that publishes nothing
- [x] 4.2 Publish an audit event for every admin write, including failures, to `audit-events`; verify the audit payload in tests
- [x] 4.3 Document the admin user API in `docs/` and the support runbook note that a password reset clears a lockout

## Workflow follow-up

- Archive the change with `/opsx:archive` after the developer has reviewed it.
