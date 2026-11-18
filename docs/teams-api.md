# Teams API

Teams group INTERNAL users; CRM services scope data access by them. The UMS database is the source of truth
(not Keycloak groups). Admin endpoints need a `crm-admin` token with the realm role `admin`, like the
[user API](admin-users-api.md). Every write needs a `ticketRef` such as `OPS-12` and is audited.
Errors are `ProblemDetail` responses.

## Admin endpoints

| Method and path | Body | Does |
|---|---|---|
| `GET /admin/teams` | | Every team, archived ones included, by name, with member and manager counts |
| `GET /admin/teams/{id}` | | One team |
| `POST /admin/teams` | `name`, `ticketRef` | Creates a team. 201 |
| `POST /admin/teams/{id}/archive` | `ticketRef` | Archives the team |
| `GET /admin/teams/{id}/members` | | The team's members with email and name, by email |
| `POST /admin/teams/{id}/members` | `sub`, `manager` (optional, default false), `ticketRef` | Adds a user. 201 |
| `PUT /admin/teams/{id}/members/{sub}` | `manager`, `ticketRef` | Sets or removes the manager mark |
| `DELETE /admin/teams/{id}/members/{sub}` | `ticketRef` (JSON body) | Removes the user from the team. 204 |
| `GET /admin/users/{sub}/teams` | | The teams a user is in |
| `PUT /admin/users/{sub}/multi-team` | `allowed`, `ticketRef` | Allows or forbids the user in more than one team |

## Rules

- **Names** are unique ignoring case: a clash is 409.
- **Members** must be INTERNAL and not DISABLED, as member or manager: otherwise 422. A user already in the team is 409.
- **Multi-team**: `multiTeamAllowed` is false by default. Joining a second team (as member or manager) without it
  is 422. Turning it off while the user is in more than one team is 409. EXTERNAL users can't have it (422).
  Concurrent adds of the same user are serialised, so the rule holds under load.
- **Archived teams** keep their members and still count in team scope, but take no new members or managers (409).
  Members can still be removed and managers unset.
- **Setting the manager mark to its current value** changes nothing and publishes nothing.
- Unknown team, unknown user, or a user who isn't in the team: 404.

## Responses

A team:

```json
{"id": "…", "name": "North Sales", "archived": false, "createdAt": "2026-11-11T10:00:00Z", "archivedAt": null,
 "memberCount": 4, "managerCount": 1}
```

A member (from the members list, add and manager change):

```json
{"teamId": "…", "sub": "…", "manager": true, "email": "ana@corp.example", "firstName": "Ana", "lastName": "Test"}
```

A user's team: `{"teamId": "…", "teamName": "North Sales", "archived": false, "manager": true}`.
The multi-team change returns `{"sub": "…", "multiTeamAllowed": true}`; users also carry `multiTeamAllowed`.

## Team scope, for CRM services

`GET /internal/users/{sub}/team-scope` needs a client-credentials token from `crm-internal`
(`JWT_INTERNAL_ISSUER_URI`) carrying scope `ums:team-scope`, such as `core-service`'s. User tokens and
`crm-admin` and `portal` tokens get 403; tokens from any other issuer get 401.

```json
{"sub": "…", "status": "ACTIVE", "memberTeams": ["…"], "managedTeams": ["…"], "allTeams": false}
```

- `memberTeams` lists every team the user is in, managed ones included; `managedTeams` the ones they manage.
- `allTeams` is true only for Super Managers: holders of the `crm-api` client role `super-manager`, directly or
  through a composite. The UMS asks Keycloak and reuses the answer for `UMS_SUPER_MANAGER_CACHE_TTL` (default 1m).
- A `sub` that isn't an INTERNAL user is 404.
- Callers should cache the scope briefly (5 minutes at most) and evict it on `TeamMembershipChanged`
  (see [events](events.md)).
