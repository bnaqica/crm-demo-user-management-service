# Spec Delta

## Purpose

Gives CRM services a single, authoritative answer to which teams a user can access and how.

## ADDED Requirements

### Requirement: Team scope lookup
The system SHALL provide `GET /internal/users/{sub}/team-scope` returning the user's status, member team ids, managed team ids and an `allTeams` flag that is true only for Super Managers.

#### Scenario: Team member
- **GIVEN** user U is a member of team A and manages none
- **WHEN** a CRM service requests U's team scope
- **THEN** the response has memberTeams [A], managedTeams [] and allTeams false

#### Scenario: Super Manager
- **WHEN** the scope of a user holding the Super Manager role is requested
- **THEN** allTeams is true and memberTeams lists only the teams they belong to

### Requirement: Scope endpoint callers
The team-scope endpoint SHALL accept only client-credentials tokens from `crm-internal` that carry scope `ums:team-scope`, and SHALL reject user tokens and admin tokens with 403.

#### Scenario: User token
- **WHEN** an internal user's own token calls the team-scope endpoint
- **THEN** the response is 403

### Requirement: Unknown user
The system SHALL return 404 for a `sub` that has no INTERNAL user record.

#### Scenario: External user sub
- **WHEN** the scope of an EXTERNAL user is requested
- **THEN** the response is 404
