# Admin user API

Every endpoint needs a JWT from the `crm-admin` realm with the realm role `admin`. A token from `crm-internal` or
`portal` gets 401; a `crm-admin` token without `admin` gets 403. Every write needs a `ticketRef` such as `OPS-12`.
Errors are `ProblemDetail` responses.

Browsers may call `/admin/**` only from the origins in `UMS_CORS_ALLOWED_ORIGINS` (the Admin UI,
`http://localhost:5175` locally).

Send `X-Correlation-Id` to tie the audit records of a request to your own logs; one is made up if you don't.

| Method and path | Body | Does |
|---|---|---|
| `GET /admin/users` | | Search users in both realms. Query: `email` and `name` (contains, ignoring case), `userType`, `status`, `page` (from 0), `size`, `sort` |
| `GET /admin/users/{sub}` | | One user |
| `POST /admin/users/internal` | `email`, `firstName`, `lastName`, `ticketRef` | Creates an INTERNAL user in `crm-internal` and emails an invite (set password and TOTP). Status INVITED |
| `POST /admin/users/external` | `email`, `firstName`, `lastName`, `role`, `ticketRef` | Invites an EXTERNAL user into `portal` with `role` (`external-starter` or `external-standard`). Status INVITED |
| `PUT /admin/users/{sub}` | `email`, `firstName`, `lastName`, `ticketRef` | Updates the profile. A new email must be verified by the user again |
| `POST /admin/users/{sub}/disable` | `ticketRef` | ACTIVE to DISABLED; disables the Keycloak account and revokes its sessions |
| `POST /admin/users/{sub}/enable` | `ticketRef` | DISABLED to ACTIVE |
| `POST /admin/users/{sub}/unlock` | `ticketRef` | Clears a brute-force lockout |
| `POST /admin/users/{sub}/logout` | `ticketRef` | Revokes all sessions |
| `POST /admin/users/{sub}/reset-mfa` | `ticketRef` | Removes OTP and WebAuthn credentials; the user sets up TOTP at next login |
| `GET /admin/users/{sub}/teams` | | The user's teams; see [teams](teams-api.md) |
| `PUT /admin/users/{sub}/multi-team` | `allowed`, `ticketRef` | Allows the user in more than one team; see [teams](teams-api.md) |

## Rules

- **Email** is the business key, unique across `crm-internal` and `portal` and compared without case. Accounts
  created in Keycloak outside the UMS count too. A clash is 409.
- **Domains**: internal users must use a domain in `UMS_CORPORATE_DOMAINS`; external users mustn't. Breaking either is 422.
- **Status**: only INVITED to ACTIVE, STARTER to ACTIVE, ACTIVE to DISABLED and DISABLED to ACTIVE. Anything else is 409.
  INVITED becomes ACTIVE on the user's first login, from the `LOGIN` event on `keycloak-events`.
- **Keycloak failures** after the Keycloak user was created delete it again, so nothing is left half-created, and a
  profile update the UMS can't save is undone in Keycloak. Keycloak errors that aren't a duplicate are 502.
- **Concurrent changes** to the same account: the second one gets 409; reload and retry.
- **Search**: an unknown `sort` property is 400.
- **Usernames** follow the email in both realms, so after a rename the old address is free again.

## Responses

A user:

```json
{
  "sub": "7c3f2d0e-0d39-4c39-9d0a-3a4c1f6f0b11",
  "userType": "INTERNAL",
  "email": "ana@corp.example",
  "firstName": "Ana",
  "lastName": "Test",
  "status": "INVITED",
  "multiTeamAllowed": false,
  "createdAt": "2026-11-11T10:00:00Z",
  "updatedAt": "2026-11-11T10:00:00Z"
}
```

A search returns `{"content": [...], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}`.
