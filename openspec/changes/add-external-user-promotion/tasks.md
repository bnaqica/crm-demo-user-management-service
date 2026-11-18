# Tasks

## 1. Promote API

- [x] 1.1 Secure `/internal/users/{id}/promote` to `crm-promoter` with `ums:promote`; verify the self-promotion 403 scenario
- [x] 1.2 Implement promotion, preconditions and idempotency; verify every `user-promotion` scenario against Testcontainers Keycloak
- [x] 1.3 Add audit with actor SYSTEM, client id and correlation id, the rate limit and the volume metric; verify the 429 scenario

## 2. Starters and cleanup

- [x] 2.1 Consume portal `REGISTER` events to record STARTER users idempotently; verify both registration scenarios
- [x] 2.2 Add ShedLock and the starter-expiry and invite-cleanup jobs; verify with a fixed `Clock`
- [x] 2.3 Document the promote API and job settings in `docs/`

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
