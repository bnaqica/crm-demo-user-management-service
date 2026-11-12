# Spec Delta

## Purpose

Keeps a durable, append-only system of record for every audit event across Keycloak, the UMS, CRM services and the portal.

## ADDED Requirements

### Requirement: Common audit record
Each stored record SHALL hold event id, time, source, realm, actor id and type, action, target type and id, outcome, ticket reference, before and after (JSON), IP address and correlation id.

#### Scenario: UMS record stored
- **WHEN** an audit event from the UMS for a user disable arrives
- **THEN** a record with source UMS, action `USER_DISABLED`, the ticket and before/after values is stored

### Requirement: Deduplicate by event id
The consumer SHALL store each event id at most once.

#### Scenario: Redelivered event
- **WHEN** the same event id is delivered twice
- **THEN** exactly one record exists

### Requirement: Keycloak events captured
The consumer SHALL store login events (success, failure, lockout, password reset, MFA) and admin events (user, role and session changes) from all three realms.

#### Scenario: Failed login
- **WHEN** a `LOGIN_ERROR` event from `crm-internal` arrives
- **THEN** a record with source KEYCLOAK, realm `crm-internal` and outcome FAILURE is stored

### Requirement: No secrets stored
The consumer SHALL remove credentials, tokens and configured sensitive user attributes from event payloads before storing them.

#### Scenario: Admin event with representation
- **WHEN** a Keycloak admin event includes a credential value in its representation
- **THEN** the stored record does not contain that value

### Requirement: Append-only
No application database role SHALL be able to UPDATE or DELETE audit records; the writer role SHALL only INSERT.

#### Scenario: Update attempted
- **WHEN** the writer role runs an UPDATE on an audit table
- **THEN** the database rejects it with a permission error

### Requirement: Invalid events quarantined
Events that can't be parsed or lack required fields SHALL be sent to `audit-events-dlq` and logged, and SHALL NOT block later events.

#### Scenario: Malformed event
- **WHEN** an event without an event id arrives
- **THEN** it is sent to the DLQ and the next event is stored normally
