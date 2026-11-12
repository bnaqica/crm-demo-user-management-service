# Spec Delta

## Purpose

Lets admins organise internal users into teams with managers, which CRM services use to scope data access.

## ADDED Requirements

### Requirement: Create and archive teams
The system SHALL let an admin create a team with a unique name and archive it with a ticket reference. An archived team SHALL keep its history but accept no new members.

#### Scenario: Duplicate team name
- **WHEN** an admin creates a team named `North Sales` and one already exists
- **THEN** the request is rejected with 409

#### Scenario: Add member to archived team
- **WHEN** an admin adds a member to an archived team
- **THEN** the request is rejected with 409

### Requirement: Internal users only
The system SHALL reject adding a user to a team, or making them a manager, unless the user's userType is INTERNAL and status is not DISABLED.

#### Scenario: External user added to team
- **WHEN** an admin adds an EXTERNAL user to a team
- **THEN** the request is rejected with 422

### Requirement: Multi-team flag
Each internal user SHALL have `multiTeamAllowed` (default false), settable only by an admin. Adding a user to a second team, as member or manager, SHALL be rejected unless the flag is true. Turning the flag off SHALL be rejected while the user is in more than one team.

#### Scenario: Second team without flag
- **GIVEN** a user in team A with `multiTeamAllowed` false
- **WHEN** an admin adds them to team B
- **THEN** the request is rejected with 422

#### Scenario: Turning the flag off while in two teams
- **GIVEN** a user in teams A and B
- **WHEN** an admin sets `multiTeamAllowed` false
- **THEN** the request is rejected with 409

### Requirement: Team managers
The system SHALL let an admin mark a team member as a manager of that team or remove the manager mark.

#### Scenario: Manager set
- **WHEN** an admin sets user U as manager of team A with a ticket
- **THEN** U's team scope lists A as a managed team

### Requirement: Membership changes need a ticket
Every team, membership, manager or flag change SHALL require a ticket reference and SHALL be audited with before and after values.

#### Scenario: Membership audited
- **WHEN** an admin removes a member with ticket `OPS-9`
- **THEN** an audit event with action `TEAM_MEMBER_REMOVED`, the ticket and before/after membership is published

### Requirement: Membership events
The system SHALL publish a `TeamMembershipChanged` event, carrying the user `sub` and the team id, after any committed membership or manager change.

#### Scenario: Event after member added
- **WHEN** an admin adds a member and the change commits
- **THEN** one `TeamMembershipChanged` event is published to `ums-user-events`
