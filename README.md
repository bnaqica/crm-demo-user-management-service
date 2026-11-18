# crm-demo-user-management-service

The User Management Service (UMS) is the only way admins manage CRM users. It creates and invites users in the
Keycloak realms `crm-internal` (employees) and `portal` (external users) through the Keycloak Admin REST API, keeps
their status, and publishes user and audit events to Kafka. See [docs](docs/README.md).

| | |
|---|---|
| Port | 8083 |
| Stack | Java 25, Spring Boot 4.1, Gradle 9.9 (wrapper), PostgreSQL, Flyway, Kafka, Keycloak Admin REST API, JUnit, Testcontainers, Spring Security (JWT), springdoc, Spotless, JaCoCo, Lombok, SLF4J (Logback), Jib |

## Prerequisites

- A JDK 17+ to run Gradle. The Java 25 toolchain is downloaded automatically by Gradle (foojay resolver)
  if you don't have it.
- In your IDE, the Lombok plugin with annotation processing enabled.
- For the feature workflow in Claude Code: Node.js 22.12+ and OpenSpec (`npm install -g @fission-ai/openspec@latest`).
- Docker running, for the local database and the Testcontainers tests. The tests start PostgreSQL, Kafka,
  Keycloak and Mailpit containers; the first run pulls their images.

## Run locally

```bash
./gradlew bootRun
```

This uses the `local` profile, which turns off authentication. Swagger UI is at
<http://localhost:8083/swagger-ui.html>. Postgres starts from `compose.yaml`; Keycloak (with Mailpit) and Kafka
come from `local-docker`:

```bash
cd ../../local-docker/keycloak && docker compose up -d
cd ../kafka && docker compose up -d
```

Invite emails land in Mailpit at <http://localhost:8025>.

## Environment variables

Needed everywhere except the `local` profile.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL of the database |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_ISSUER_URI` | The `crm-admin` realm, the only issuer whose JWTs this service accepts |
| `KEYCLOAK_BASE_URL` | Keycloak's address, for the Admin REST API and service-account tokens |
| `KEYCLOAK_UMS_INTERNAL_SECRET` | Secret of the `ums-internal` client in `crm-internal` |
| `KEYCLOAK_UMS_PORTAL_SECRET` | Secret of the `ums-portal` client in `portal` |
| `KEYCLOAK_UMS_ADMIN_SECRET` | Secret of the `ums-admin` client in `crm-admin` (admin accounts only) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers |
| `AUDIT_DB_URL` | JDBC URL of the central audit database, `crm_audit_db` |
| `AUDIT_DB_OWNER_USERNAME`, `AUDIT_DB_OWNER_PASSWORD` | Owner of the audit schema; runs its migrations |
| `AUDIT_DB_WRITER_USERNAME`, `AUDIT_DB_WRITER_PASSWORD` | INSERT-only role the audit consumer writes with |
| `AUDIT_DB_READER_USERNAME`, `AUDIT_DB_READER_PASSWORD` | SELECT-only role the audit search reads with |
| `UMS_CORPORATE_DOMAINS` | Comma-separated employee email domains |
| `JWT_PORTAL_ISSUER_URI` | The `portal` realm; its tokens are refused on `/internal/**` with 403 |
| `JWT_INTERNAL_ISSUER_URI` | The `crm-internal` realm, whose client-credentials tokens may call `/internal/**` |

Optional: `UMS_INVITE_LIFESPAN` (default `72h`), `UMS_LOCKOUT_ALERT_THRESHOLD` (default `5`),
`UMS_LOCKOUT_ALERT_WINDOW` (default `5m`), `UMS_OUTBOX_RELAY_INTERVAL` (default `1s`),
`UMS_SUPER_MANAGER_CACHE_TTL` (how long a Super Manager lookup is reused for team scope, default `1m`),
`UMS_PROMOTION_RATE_LIMIT` (promote calls per minute and instance, default `60`),
`UMS_CLEANUP_CRON` (when the starter-expiry and invite-cleanup jobs run, default `0 0 3 * * *`),
`UMS_STARTER_EXPIRY_DAYS` (a portal starter not promoted within this is disabled, default `30`),
`UMS_AUDIT_SENSITIVE_ATTRIBUTES` (user attributes never stored in audit records, comma-separated; default none),
`UMS_AUDIT_SEARCH_MAX_RANGE` (longest audit search range, default `90d`),
`UMS_AUDIT_PARTITION_CRON` (when next month's audit partition is created, default `0 30 2 * * *`),
`UMS_GRANT_REQUEST_TTL` (how long a privileged grant waits for a second admin, default `72h`),
`UMS_BREAK_GLASS_ADMIN_ID` (Keycloak id of the break-glass admin, whose logins raise an alert; default none),
`UMS_CORS_ALLOWED_ORIGINS` (comma-separated browser origins allowed on `/admin/**`, such as the Admin UI; default none).

## Test and format

```bash
./gradlew build
```

The build runs the tests, writes a coverage report to `build/reports/jacoco/test/html/index.html`, and fails if
the code isn't formatted. To format it:

```bash
./gradlew spotlessApply
```

## Build a container (Jib)

```bash
./gradlew jibDockerBuild
```

To push to a registry instead:

```bash
IMAGE_REGISTRY=ghcr.io/my-org ./gradlew jib
```

## Contributing

Package layout and conventions are in `CLAUDE.md` and `.claude/rules/`.
