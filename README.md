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
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers |
| `UMS_CORPORATE_DOMAINS` | Comma-separated employee email domains |

Optional: `UMS_INVITE_LIFESPAN` (default `72h`), `UMS_LOCKOUT_ALERT_THRESHOLD` (default `5`),
`UMS_LOCKOUT_ALERT_WINDOW` (default `5m`), `UMS_OUTBOX_RELAY_INTERVAL` (default `1s`).

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
