# Design

## Context

See proposal.md. Core-service calls promote using the `crm-promoter` client (core `add-submission-intake`).

## Decisions

- **Caller check** uses the `crm-internal` issuer decoder from `add-teams-and-membership`, plus `azp == crm-promoter` and `SCOPE_ums:promote`. Checking both the client and the scope means a scope leaked to another client is not enough.
- **Idempotency** comes from the state check: an already promoted user is a no-op. The role swap is done as add-standard, then remove-starter, so a failure between them leaves the user with more access rather than none, and a retry completes it.
- **Rate limit** with a Bucket4j in-memory bucket per instance; alerting through a Micrometer counter. Alternative: gateway rate limiting — no gateway yet.
- **Jobs** are `@Scheduled` with ShedLock (one runner across instances); expiry days and invite TTL in `@ConfigurationProperties`, using the injected `Clock`.

## Risks / Trade-offs

- [In-memory rate limit is per instance] → acceptable for now; move to Redis if scaled out.

## Implementation decisions

Recorded while applying the change.

- **Portal tokens on `/internal/**`.** The spec wants a portal user's own token to get 403, so the `/internal/**`
  chain also trusts the `portal` issuer (`JWT_PORTAL_ISSUER_URI`), giving its tokens no authorities. That also turns
  a portal token on team-scope from 401 into 403, which matches team-scope's "user tokens get 403".
- **Caller check**: a client-credentials token (`client_id == azp`) from `crm-internal` with scope `ums:promote`
  and `azp == crm-promoter`.
- **Which users promote.** EXTERNAL, not DISABLED, holding `external-starter`: `external-standard` is added, then
  `external-starter` removed, and STARTER becomes ACTIVE (an INVITED user stays INVITED until first login; an ACTIVE
  one stays ACTIVE). Holding both roles (an interrupted earlier call) is completed the same way. Already holding only
  `external-standard` is the idempotent no-op: 200 with the current state, nothing published. The response is
  `{"sub", "status", "roles"}`.
- **Audit** is `USER_PROMOTED`, actorType SYSTEM with the calling client id, the request's `X-Correlation-Id`, and
  SUCCESS or FAILURE (refused calls, including 409, 404 and 429, are audited as FAILURE). No ticket: it is a system call.
- **Rate limit and volume alert.** One Bucket4j bucket per instance, `UMS_PROMOTION_RATE_LIMIT` calls per minute
  (default 60). A refused call is 429 and raises the `promotion-volume` alert, at most once a minute. Every call
  counts in the Micrometer counter `ums.promotions{outcome}`.
- **Registrations.** A `REGISTER` event from `portal` creates the STARTER record from the Keycloak user and adds
  `external-starter` if missing; a known sub is skipped, so redelivery is harmless. An email that already belongs to
  another account (crm-internal) can't be recorded: it is logged and raises the `registration-email-clash` alert.
- **Jobs** run daily (`UMS_CLEANUP_CRON`, default 03:00) under ShedLock (`shedlock` table, database time).
  Starter expiry disables STARTER users registered more than `UMS_STARTER_EXPIRY_DAYS` (default 30) days ago in
  Keycloak and in the UMS, stores `STARTER_EXPIRED` in the new `user_account.status_reason`, publishes
  `UserDisabled` and writes a SYSTEM audit. Invite cleanup deletes INVITED users (never signed in) whose invite
  (`UMS_INVITE_LIFESPAN`) has expired: the Keycloak account, their team memberships (publishing
  `TeamMembershipChanged`) and the record, with a SYSTEM `USER_DELETED` audit. The job methods take the time as an
  argument, which the scheduled trigger reads from the injected `Clock`; tests pass a fixed instant.
- **Dependencies** not in the Spring Boot BOM: Bucket4j 8.21.0 and ShedLock 7.10.1, pinned in `build.gradle`.
- **Code review (code-reviewer agent).** No Critical findings. Fixed: a STARTER who already holds only
  `external-standard` (Keycloak done, UMS step failed, or roles changed by an admin) is now activated rather than
  answered as promoted; every promote call is audited, the idempotent one with before == after; the cleanup jobs
  re-check each account under a row lock (a promotion or first login that came first is respected, one in progress
  waits); invite cleanup keeps an invitee who has already set a password in Keycloak (their LOGIN event may be late
  or lost); the `azp == crm-promoter` guard has its own test; one cleanup part failing no longer stops the other, and
  the job lock is held at least a minute.
  Deferred (minor): `status_reason` is not cleared when an expired starter is enabled again; STARTER to DISABLED is
  set by the job directly rather than through `UserStatus.canBecome`; a registration whose email clashes keeps the
  `external-starter` role in Keycloak without a UMS record; a deleted invitee gets no `UserDeleted` event (only the
  audit and `TeamMembershipChanged`); the rate limit and expiry days use `@Value` rather than `UmsProperties`;
  `ums.promotions{outcome=refused}` also counts server errors; there is no API-level 429 test (the limiter and the
  429 mapping are unit-tested).
