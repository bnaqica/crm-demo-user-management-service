# Spec Delta

## Purpose

Lets admins create, invite and maintain internal (crm-internal) and external (portal) user accounts, keeping Keycloak as the identity source and the UMS as the only management path.

## ADDED Requirements

### Requirement: Create internal user
The system SHALL let an admin create an INTERNAL user in the `crm-internal` realm with an email, name and ticket reference, and send an invite email with required actions UPDATE_PASSWORD and CONFIGURE_TOTP whose link expires within 24 to 72 hours. The new account SHALL have status INVITED.

#### Scenario: Internal user invited
- **WHEN** an admin posts a new internal user with email `ana@corp.example` and ticket `OPS-12`
- **THEN** the user exists in `crm-internal` with required actions UPDATE_PASSWORD and CONFIGURE_TOTP
- **AND** the response returns the Keycloak `sub`, userType INTERNAL and status INVITED

#### Scenario: Ticket reference missing
- **WHEN** an admin creates a user without a ticket reference
- **THEN** the request is rejected with 400 and no Keycloak user is created

### Requirement: Invite external user
The system SHALL let an admin invite an EXTERNAL user into the `portal` realm with a pre-assigned portal role. The account SHALL have status INVITED and receive an expiring invite email.

#### Scenario: External user invited
- **WHEN** an admin invites `client@acme.example` with role `external-standard`
- **THEN** the user exists in `portal` with that role and status INVITED

#### Scenario: Internal role requested for external user
- **WHEN** an admin invites an external user with a role whose assignableTo does not include EXTERNAL
- **THEN** the request is rejected with 422

### Requirement: Email unique across realms
The system SHALL treat email (case-insensitive) as the business key and reject creating an account whose email already exists in either `crm-internal` or `portal`.

#### Scenario: Same email in the other realm
- **GIVEN** `pat@acme.example` exists in `portal`
- **WHEN** an admin creates an internal user with `PAT@acme.example`
- **THEN** the request is rejected with 409

### Requirement: User type is explicit
The system SHALL require userType (INTERNAL or EXTERNAL) on creation and SHALL reject an INTERNAL user whose email domain is not on the configured corporate-domain list, and an EXTERNAL user whose email domain is on it.

#### Scenario: Corporate domain used for external user
- **WHEN** an admin invites an external user with an email on a corporate domain
- **THEN** the request is rejected with 422

### Requirement: Account status transitions
The system SHALL allow only these transitions: INVITED→ACTIVE, STARTER→ACTIVE, ACTIVE→DISABLED and DISABLED→ACTIVE, and SHALL reject any other transition.

#### Scenario: Disabling an invited user
- **WHEN** an admin disables a user whose status is INVITED
- **THEN** the request is rejected with 409

#### Scenario: Invited user completes setup
- **WHEN** an INVITED user completes their required actions and logs in for the first time
- **THEN** their status becomes ACTIVE

### Requirement: Disable revokes sessions
The system SHALL, when an admin disables a user, disable the Keycloak account and revoke all of the user's sessions in the same operation, and publish a `UserDisabled` event.

#### Scenario: User disabled
- **WHEN** an admin disables an ACTIVE user with ticket `OPS-40`
- **THEN** the user is disabled in Keycloak, has no active sessions, has status DISABLED, and a `UserDisabled` event is published

### Requirement: Unlock user
The system SHALL provide `POST /admin/users/{id}/unlock` that clears the user's brute-force lockout through Keycloak's attack-detection API. It SHALL require a ticket reference.

#### Scenario: Locked user unlocked
- **GIVEN** a user locked out after failed logins
- **WHEN** an admin calls unlock with a ticket reference
- **THEN** the user can log in again and an audit record with the ticket is written

### Requirement: Mass lockout alert
The system SHALL raise an alert when more than a configured number of accounts become locked within a configured time window.

#### Scenario: Several accounts lock at once
- **WHEN** lockout events for 10 accounts arrive within 5 minutes and the threshold is 5 per 5 minutes
- **THEN** an alert is emitted to admins

### Requirement: Force logout and MFA reset
The system SHALL let an admin, with a ticket reference, revoke all sessions of a user, and reset a user's MFA so the user must configure it again on the next login.

#### Scenario: MFA reset
- **WHEN** an admin resets MFA for a user with ticket `OPS-77`
- **THEN** the user's OTP and WebAuthn credentials are removed and CONFIGURE_TOTP is set as a required action

### Requirement: Update user profile
The system SHALL let an admin update a user's first name, last name and email with a ticket reference, keeping the email unique across realms.

#### Scenario: Email changed
- **WHEN** an admin changes a user's email to an unused address
- **THEN** Keycloak holds the new email, the `sub` is unchanged and the user must verify the new email

### Requirement: Search users
The system SHALL let an admin list users from both realms, filtered by email, name, userType and status, with pagination.

#### Scenario: Filter by status
- **WHEN** an admin lists users with status DISABLED
- **THEN** only DISABLED users from both realms are returned

### Requirement: User events
The system SHALL publish `UserCreated` and `UserDisabled` events with event id, schema version, user `sub`, userType and email through a transactional outbox, so an event is published only if its change committed.

#### Scenario: Keycloak call fails
- **WHEN** creating the Keycloak user fails
- **THEN** no `UserCreated` event is published and no local record remains

### Requirement: Admin actions audited
The system SHALL record every admin write (success or failure) as an audit event with actor, target, action, before/after values, ticket reference, outcome and correlation id.

#### Scenario: Audit of a disable
- **WHEN** an admin disables a user
- **THEN** an audit event with action `USER_DISABLED`, the admin as actor, before status ACTIVE and after status DISABLED is published to `audit-events`
