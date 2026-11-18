# Portal users: promotion, starters and cleanup

## Promote a starter: `POST /internal/users/{sub}/promote`

For the CRM only: a client-credentials token from `crm-internal` for client `crm-promoter`, with scope
`ums:promote`. Any other token gets 403: user tokens (the user's own included), admin tokens, other services.
Tokens from an unknown issuer get 401. No body. Send `X-Correlation-Id`; it is copied into the audit record.

| Situation | Answer |
|---|---|
| EXTERNAL, not DISABLED, holding `external-starter` | `external-standard` added, then `external-starter` removed; STARTER becomes ACTIVE; `UserPromoted` published. 200 |
| Holding both roles (an earlier call stopped half way) | Finished as above. 200 |
| Already promoted: holding only `external-standard` | Nothing changes or is published. 200 with the current state |
| INTERNAL, DISABLED, or holding neither portal role | 409 |
| Unknown sub | 404 |
| More than `UMS_PROMOTION_RATE_LIMIT` calls this minute (per instance) | 429, and the `promotion-volume` alert (at most once a minute) |

Response: `{"sub": "…", "status": "ACTIVE", "roles": ["external-standard"]}`.

Every call is audited as `USER_PROMOTED` with actorType `SYSTEM`, actor `crm-promoter`, the correlation id and the
outcome (refusals as FAILURE). The Micrometer counter `ums.promotions{outcome=success|refused}` counts calls.

## Self-registered starters

A `REGISTER` event from the `portal` realm on `keycloak-events` records the user as EXTERNAL with status STARTER
(from the Keycloak account: email and names), adds `external-starter` if missing and publishes `UserCreated`.
A sub already recorded is skipped, so redelivered events change nothing. If the email already belongs to another
account, the user isn't recorded and the `registration-email-clash` alert is raised.

## Cleanup jobs

Both run on `UMS_CLEANUP_CRON` (default `0 0 3 * * *`, daily at 03:00) on one instance at a time (ShedLock,
table `shedlock`).

- **Starter expiry**: STARTER users registered more than `UMS_STARTER_EXPIRY_DAYS` (default 30) days ago are disabled
  in Keycloak (sessions ended) and in the UMS, with `statusReason` `STARTER_EXPIRED`. `UserDisabled` is published
  and a SYSTEM `USER_DISABLED` audit record carries the reason.
- **Invite cleanup**: INVITED users (never signed in) whose invite expired (`UMS_INVITE_LIFESPAN` after creation) are
  deleted: the Keycloak account, their team memberships (`TeamMembershipChanged`) and the record. A SYSTEM
  `USER_DELETED` audit record carries the reason `INVITE_EXPIRED`.

A failure on one account is logged and retried on the next run; the others go on.
