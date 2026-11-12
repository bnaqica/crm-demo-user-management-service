# Spec Delta

## Purpose

Lets admins define roles as bundles of catalog permissions and assign them to users only where they are allowed.

## ADDED Requirements

### Requirement: Role definitions
The system SHALL let an admin create and edit a role with a unique name, description, owner, `assignableTo` (INTERNAL or EXTERNAL) and a set of catalog permissions from the same realm. Changing a role SHALL update the Keycloak composite role.

#### Scenario: Mixed-realm permissions
- **WHEN** an admin saves a role with a CRM permission and a portal permission
- **THEN** the request is rejected with 422

### Requirement: Assignability
The system SHALL reject assigning a role to a user whose userType is not in the role's `assignableTo`, and SHALL reject assigning any CRM or portal role to an admin account.

#### Scenario: Internal role on external user
- **WHEN** an admin assigns Team Member to an EXTERNAL user
- **THEN** the request is rejected with 422

### Requirement: Super Manager bundle
The Super Manager role SHALL include every non-sensitive CRM permission, including ones registered later, and SHALL include a sensitive permission only after an admin adds it explicitly. It SHALL be assignable only to INTERNAL users and SHALL never include admin permissions.

#### Scenario: New non-sensitive permission
- **WHEN** core-service registers `notes:read` with sensitive false
- **THEN** Super Manager holds `notes:read`

#### Scenario: New sensitive permission
- **WHEN** core-service registers `exports:write` with sensitive true
- **THEN** Super Manager does not hold `exports:write`

### Requirement: Assign and revoke roles
The system SHALL let an admin assign and revoke roles with a ticket reference, write an audit event with before/after roles, and publish a `RoleAssigned` event.

#### Scenario: Role assigned
- **WHEN** an admin assigns Team Manager to an internal user with ticket `OPS-3`
- **THEN** the user holds the role in Keycloak, an audit event is written and `RoleAssigned` is published
