# Spec Delta

## Purpose

Defines who may use the UMS admin API: only admins signed in to the crm-admin realm.

## ADDED Requirements

### Requirement: Admin tokens only
Every `/admin/**` endpoint SHALL accept only JWTs issued by the `crm-admin` realm that carry the `admin` role, and SHALL reject tokens from `crm-internal` or `portal` with 401.

#### Scenario: CRM user token
- **WHEN** a request to `/admin/users` carries a valid `crm-internal` token
- **THEN** the response is 401

#### Scenario: Admin token
- **WHEN** a request carries a `crm-admin` token with role `admin`
- **THEN** the request is processed
