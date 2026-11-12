# Proposal

## Why

Every privileged action must be audited, and Keycloak keeps its own events only briefly. The architecture calls for one append-only Postgres audit database, separate from the UMS, CRM and portal databases, fed by events from Keycloak, the UMS, CRM services and the portal.

## What Changes

- A single audit consumer, hosted in the UMS, that reads `audit-events` and `keycloak-events`, deduplicates by event id, strips secrets and inserts into a separate audit database.
- Append-only storage: the writer role can only INSERT; tables partitioned by month; optional hash chain.
- A read-only audit search endpoint for the Admin UI.

## Capabilities

### New Capabilities
- `audit-store`: ingestion and storage of audit records from all sources.
- `audit-search`: admin search over the audit records.

## Impact

- New Postgres database `crm_audit_db` with its own Flyway migrations and two roles (`audit_writer` INSERT-only, `audit_reader` SELECT-only), added to `local-docker/postgres`.
- A second DataSource in the UMS.
- Depends on the Keycloak event-listener SPI publishing to `keycloak-events` (delivered in `local-docker/keycloak`; outside this repo).
