# Spec Delta

## ADDED Requirements

### Requirement: Admin accounts are exclusive
The system SHALL reject assigning CRM roles, portal roles or team membership to an account in the `crm-admin` realm.

#### Scenario: CRM role for an admin
- **WHEN** an admin assigns a CRM role to an admin account
- **THEN** the request is rejected with 422

### Requirement: Last admin protected
The system SHALL reject disabling or deleting the last enabled admin account in `crm-admin`.

#### Scenario: Disable the last admin
- **GIVEN** exactly one enabled admin exists
- **WHEN** a request disables that admin
- **THEN** the request is rejected with 409

### Requirement: Break-glass alert
The system SHALL emit an alert whenever a login event for the configured break-glass admin account is received.

#### Scenario: Break-glass login
- **WHEN** a login event for the break-glass account arrives
- **THEN** an alert is emitted and an audit record is written
