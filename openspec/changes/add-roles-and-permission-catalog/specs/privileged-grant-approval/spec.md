# Spec Delta

## Purpose

Requires a second admin to approve the most powerful grants, so no single admin can create a Super Manager or another admin alone.

## ADDED Requirements

### Requirement: Privileged grants need approval
Assigning Super Manager or creating an admin account SHALL create a pending grant request instead of applying the change.

#### Scenario: Super Manager requested
- **WHEN** admin A assigns Super Manager to user U
- **THEN** a PENDING grant request is created and U does not yet hold the role

### Requirement: Second admin approves
A pending grant request SHALL be applied only when a different admin approves it, and SHALL be closed without effect when any admin rejects it or it is older than the configured expiry.

#### Scenario: Requester approves own request
- **WHEN** admin A approves the request A created
- **THEN** the response is 403 and the request stays PENDING

#### Scenario: Another admin approves
- **WHEN** admin B approves A's request
- **THEN** the grant is applied and the request becomes APPROVED, with both admins in the audit record

### Requirement: Approvals list
The system SHALL let admins list grant requests filtered by status.

#### Scenario: Pending list
- **WHEN** an admin lists PENDING requests
- **THEN** only pending, unexpired requests are returned
