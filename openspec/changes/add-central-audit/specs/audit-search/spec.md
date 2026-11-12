# Spec Delta

## Purpose

Lets admins investigate who did what, when and to whom, without any ability to change audit history.

## ADDED Requirements

### Requirement: Search audit records
The system SHALL provide `GET /admin/audit` for admins, filtering by time range, source, realm, actor, target, action, outcome, ticket reference and correlation id, newest first, with pagination.

#### Scenario: Search by ticket
- **WHEN** an admin searches for ticket `OPS-12`
- **THEN** every record carrying `OPS-12` is returned, newest first

### Requirement: Bounded queries
A search SHALL require a time range no longer than a configured maximum, and SHALL reject wider ranges with 400.

#### Scenario: Range too wide
- **WHEN** an admin searches a two-year range with a 90-day maximum
- **THEN** the response is 400

### Requirement: Read-only
The audit API SHALL expose no endpoint that creates, changes or deletes records.

#### Scenario: Delete attempted
- **WHEN** an admin sends DELETE to an audit record URL
- **THEN** the response is 405
