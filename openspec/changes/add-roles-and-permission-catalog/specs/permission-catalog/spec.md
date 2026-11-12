# Spec Delta

## Purpose

Keeps a single, code-sourced list of every permission and UI feature so admins can grant only what services actually enforce.

## ADDED Requirements

### Requirement: Services register permissions
The system SHALL accept from an authorised service a full list of its permissions, each with key (`<resource>:read`, `<resource>:write` or `ui:<feature>`), realm, description and `sensitive` flag, and SHALL make the stored catalog for that service match the list.

#### Scenario: New permission registered
- **WHEN** core-service registers `deals:read` and `deals:write`
- **THEN** both appear in the catalog for core-service and are created as `crm-api` client roles

#### Scenario: Permission removed from code
- **WHEN** a registration no longer includes `reports:read`
- **THEN** `reports:read` is marked deprecated and is no longer grantable, but existing grants are reported to admins rather than silently removed

### Requirement: Write includes read
Granting a `<resource>:write` permission SHALL always also grant `<resource>:read`.

#### Scenario: Role with write only
- **WHEN** a role is saved with `deals:write` and without `deals:read`
- **THEN** the effective permissions of the role include `deals:read`

### Requirement: Catalog is read-only for admins
The system SHALL let admins list and filter the catalog and SHALL NOT let them create, rename or delete permissions.

#### Scenario: Admin creates a permission
- **WHEN** an admin posts a new permission to the catalog
- **THEN** the response is 405

### Requirement: Registration callers
Catalog registration SHALL accept only client-credentials tokens carrying scope `ums:catalog`, and a service SHALL only change its own entries.

#### Scenario: Service overwrites another service
- **WHEN** core-service registers permissions under portal-service's name
- **THEN** the response is 403
