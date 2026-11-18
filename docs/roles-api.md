# Roles, permission catalog and approvals API

Admin endpoints need a `crm-admin` token with the realm role `admin`, like the [user API](admin-users-api.md).
Every write needs a `ticketRef` such as `OPS-12` and is audited. Errors are `ProblemDetail` responses.

## How it maps to Keycloak

- A **permission** is a client role: of `crm-api` for realm `crm-internal`, of `portal-app` for realm `portal`.
  core-service reads `resource_access.crm-api.roles`.
- A **role** is a composite client role of the same client, named like the role, holding its permissions.
  The built-in portal roles `external-starter` and `external-standard` are realm roles (the portal service reads
  those); each is a composite of the `portal-app` client role of the same name, which holds its permissions.
- The UMS creates missing client roles itself, including built-in roles on first use.

## Permission catalog

Written only by the services that enforce the permissions; read-only for admins.

| Method and path | Who | Does |
|---|---|---|
| `PUT /internal/catalog/{service}` | `crm-internal` client-credentials token with scope `ums:catalog`, whose `client_id` is `{service}` | Replaces the service's catalog. 200 |
| `GET /admin/catalog` | admin | The catalog. Query: `service`, `realm` (`crm-internal` or `portal`), `deprecated` (true/false) |

`POST`, `PUT` and `DELETE` on `/admin/catalog` answer 405.

Registration body (every permission the service enforces, every time it starts):

```json
{"permissions": [
  {"key": "deals:read", "realm": "crm-internal", "description": "Read deals", "sensitive": false},
  {"key": "deals:write", "realm": "crm-internal", "description": "Change deals", "sensitive": false}
]}
```

- `key` is `<resource>:read`, `<resource>:write` or `ui:<feature>`, lower-case (letters, digits, hyphens); otherwise 400.
  `sensitive` defaults to false. A key listed twice is 422; a key another service registered is 409.
- What the service leaves out is **deprecated**: still listed (with the roles holding it, in `grantedBy`) but no
  longer grantable. Nothing is taken away from existing roles; admins clean up. Registering it again revives it.
- Every new non-sensitive `crm-internal` permission is added to **Super Manager**. Sensitive ones are added only by
  an admin editing the role.
- A service whose `client_id` doesn't match `{service}`, a token without `ums:catalog`, and admin or user tokens get 403.
- The database is updated first, then Keycloak; a Keycloak failure (502) is repaired by the next registration.

Response: `{"service": "core-service", "active": ["deals:read", "deals:write"], "deprecated": ["reports:read"]}`.

A catalog entry:

```json
{"key": "reports:read", "service": "core-service", "realm": "crm-internal", "description": "Read reports",
 "sensitive": false, "deprecated": true, "deprecatedAt": "2026-11-12T10:00:00Z", "grantedBy": ["team-manager"]}
```

## Roles

| Method and path | Body | Does |
|---|---|---|
| `GET /admin/roles` | | Every role, by name |
| `GET /admin/roles/{name}` | | One role |
| `POST /admin/roles` | `name`, `description`, `owner`, `assignableTo` (`INTERNAL` or `EXTERNAL`), `permissions` (keys), `ticketRef` | Creates a role. 201 |
| `PUT /admin/roles/{name}` | `description`, `owner`, `permissions`, `ticketRef` | Changes a role; its Keycloak composite follows |

- `name` is lower-case letters, digits and hyphens (it is the Keycloak role name); a clash is 409. Name and
  `assignableTo` never change. Roles aren't deleted.
- Permissions must be in the catalog of the role's realm (`assignableTo`) and not deprecated: a permission of the
  other realm, an unknown one or a deprecated one is 422.
- **Write includes read**: a `<resource>:write` always brings `<resource>:read`.
- Built-in roles: `super-manager`, `team-manager`, `team-member` (INTERNAL), `external-starter`, `external-standard`
  (EXTERNAL).

A role:

```json
{"name": "team-manager", "description": "Manages a team's CRM data", "owner": "ums", "assignableTo": "INTERNAL",
 "builtIn": true, "permissions": ["deals:read", "deals:write"], "createdAt": "…", "updatedAt": "…"}
```

## A user's roles

| Method and path | Body | Does |
|---|---|---|
| `GET /admin/users/{sub}/roles` | | `{"sub": "…", "roles": ["team-member"]}` |
| `POST /admin/users/{sub}/roles` | `role`, `ticketRef` | Assigns. 200 with the user's roles, or **202 with a grant request** for `super-manager` |
| `DELETE /admin/users/{sub}/roles/{role}` | `ticketRef` (JSON body) | Revokes. 200 with the user's roles; 404 if the user doesn't hold it |

- A role whose `assignableTo` isn't the user's type is 422. An admin account (crm-admin) gets 422 for any role or
  team: admin accounts are exclusive.
- Assigning a role the user already holds changes and publishes nothing.
- Each change publishes `RoleAssigned` or `RoleRevoked` and an audit event with the roles before and after
  (see [events](events.md)).

## Privileged grant approval

Assigning Super Manager and creating an admin account need a second admin.

| Method and path | Body | Does |
|---|---|---|
| `POST /admin/admins` | `email`, `firstName`, `lastName`, `ticketRef` | Asks for a new admin account. 202 with the grant request. The email must be unused in every realm (409) |
| `POST /admin/admins/{id}/disable` | `ticketRef` | Disables an admin account and ends its sessions |
| `DELETE /admin/admins/{id}` | `ticketRef` (JSON body) | Deletes an admin account. 204 |
| `GET /admin/grant-requests` | | Grant requests, newest first. Query: `status` (`PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`) |
| `GET /admin/grant-requests/{id}` | | One request |
| `POST /admin/grant-requests/{id}/approve` | `ticketRef` | Applies the change. The requester gets 403; a closed or expired request is 409 |
| `POST /admin/grant-requests/{id}/reject` | `ticketRef` | Closes it without effect. Any admin, the requester included |

- A request expires after `UMS_GRANT_REQUEST_TTL` (default 72h); from then on it shows as EXPIRED and can't be
  approved. `status=PENDING` lists only unexpired ones.
- An approved admin account is created in `crm-admin` with the realm role `admin` and gets an email to set a
  password and TOTP.
- **Last admin**: disabling or deleting the last enabled admin account is 409. An id that isn't a crm-admin account is 404.
- **Break-glass**: a login of the account whose Keycloak id is `UMS_BREAK_GLASS_ADMIN_ID` raises the
  `break-glass-login` alert and writes a `BREAK_GLASS_LOGIN` audit record.

A grant request:

```json
{"id": "…", "type": "SUPER_MANAGER", "target": "<user sub>", "status": "PENDING", "requestedBy": "<admin sub>",
 "decidedBy": null, "ticketRef": "OPS-21", "details": {"role": "super-manager"},
 "createdAt": "…", "decidedAt": null, "expiresAt": "…"}
```

For `ADMIN_ACCOUNT` the target is the email and `details` holds `email`, `firstName` and `lastName`.
