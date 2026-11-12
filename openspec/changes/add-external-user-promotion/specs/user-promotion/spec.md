# Spec Delta

## Purpose

Moves an external portal user from starter to standard access through a controlled, audited system call.

## ADDED Requirements

### Requirement: Promote starter to standard
`POST /internal/users/{id}/promote` SHALL, for a user with userType EXTERNAL, status STARTER and role `external-starter`, replace that role with `external-standard`, set status ACTIVE and publish `UserPromoted`.

#### Scenario: Starter promoted
- **GIVEN** an EXTERNAL user with status STARTER and role `external-starter`
- **WHEN** `crm-promoter` calls promote with a correlation id
- **THEN** the user holds `external-standard` and not `external-starter`, status is ACTIVE and `UserPromoted` is published

### Requirement: Promotion callers
The promote endpoint SHALL accept only client-credentials tokens from client `crm-promoter` with scope `ums:promote`; any user token, including the user's own, SHALL get 403.

#### Scenario: User promotes themselves
- **WHEN** a portal user's token calls promote for their own id
- **THEN** the response is 403 and nothing changes

### Requirement: Promotion is idempotent
Promoting a user who is already EXTERNAL, ACTIVE and `external-standard` SHALL return success without changing anything or publishing another event.

#### Scenario: Promote twice
- **WHEN** promote is called again for an already promoted user
- **THEN** the response is 200 with the current state and no new `UserPromoted` event

### Requirement: Preconditions
Promote SHALL return 409 for an INTERNAL user, a DISABLED user, or an EXTERNAL user that holds neither starter nor standard role, and 404 for an unknown user.

#### Scenario: Internal user
- **WHEN** promote is called for an INTERNAL user
- **THEN** the response is 409

### Requirement: Promotion audited and limited
Every promote call SHALL be audited with actor SYSTEM, the calling client id, the correlation id and the outcome. Calls above a configured rate SHALL get 429 and an alert SHALL be raised on unusual volume.

#### Scenario: Rate exceeded
- **WHEN** promote calls exceed the configured per-minute limit
- **THEN** further calls get 429 and an alert is emitted
