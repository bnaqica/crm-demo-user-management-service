# Spec Delta

## Purpose

Tracks self-registered portal users from their first registration and removes accounts that were never activated.

## ADDED Requirements

### Requirement: Self-registered users recorded as starters
When the `portal` realm reports a self-registration, the system SHALL record the user as EXTERNAL with status STARTER and ensure the user holds `external-starter`.

#### Scenario: Registration event
- **WHEN** a `REGISTER` event for the `portal` realm arrives
- **THEN** a STARTER user record exists for that `sub` and a `UserCreated` event is published

#### Scenario: Duplicate event
- **WHEN** the same registration event arrives twice
- **THEN** only one user record and one `UserCreated` event exist

### Requirement: Unpromoted starters expire
The system SHALL disable starters not promoted within the configured number of days and record the reason `STARTER_EXPIRED`.

#### Scenario: Starter past expiry
- **GIVEN** a starter registered 31 days ago and an expiry of 30 days
- **WHEN** the expiry job runs
- **THEN** the user is DISABLED with reason `STARTER_EXPIRED` and an audit event is written

### Requirement: Unused invites cleaned up
The system SHALL delete invited accounts whose invite link expired without first login and record an audit event.

#### Scenario: Invite expired
- **GIVEN** an INVITED user whose invite expired yesterday
- **WHEN** the cleanup job runs
- **THEN** the Keycloak account and user record are removed and an audit event is written
