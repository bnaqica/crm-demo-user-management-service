# Tasks

## 1. Data model

- [ ] 1.1 Add `V4__team.sql` (team, team_member, multi_team_allowed) and verify the migration in the Testcontainers test

## 2. Teams admin API

- [ ] 2.1 Implement team create and archive with CRUD controller and service; verify duplicate-name and archived scenarios
- [ ] 2.2 Implement add/remove member and set/unset manager with the internal-only and multi-team rules; verify each `teams` scenario, including a concurrent-add test
- [ ] 2.3 Implement setting `multiTeamAllowed`; verify the turn-off-while-in-two-teams scenario
- [ ] 2.4 Publish audit events and `TeamMembershipChanged` through the outbox; verify payloads and that a rolled-back change publishes nothing

## 3. Team scope API

- [ ] 3.1 Add the `crm-internal` issuer decoder for `/internal/**` requiring scope `ums:team-scope`; verify user and admin tokens get 403
- [ ] 3.2 Implement `GET /internal/users/{sub}/team-scope`; verify the member, Super Manager and unknown-user scenarios
- [ ] 3.3 Document the team and team-scope APIs in `docs/`

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
