# Support runbook

## A user is locked out

A **password reset clears a brute-force lockout**, so first send the user to "Forgot password" on the sign-in page.
Most users unlock themselves this way.

Use the admin unlock (`POST /admin/users/{sub}/unlock`) only when the user can't reach their email, or the lockout
looks suspicious. Before unlocking, verify the user's identity through their manager or a known phone number, and
record the ticket reference.

Several accounts locking within a few minutes raises a `mass-lockout` alert: treat it as a possible attack before
unlocking anyone.

## A user lost their MFA device

MFA resets are admin-only and by ticket: verify identity as above, then `POST /admin/users/{sub}/reset-mfa`.
The user sets up TOTP again at their next login.

## Someone leaves

Disable the account the same day the leaver notice arrives: `POST /admin/users/{sub}/disable`. This also signs them
out everywhere.
