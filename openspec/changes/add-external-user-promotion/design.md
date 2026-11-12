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
