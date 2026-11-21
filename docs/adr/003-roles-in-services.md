# ADR-003: Permission checks in the services

- **Status:** Accepted
- **Date:** 2026-11-18

## Context

Rules like "an employee may use only accounts of their office" or "only the receiver confirms a transfer" depend on data, not only on the role.

## Decision

Spring Security handles login and "authenticated or not". The business permissions are checked in the service methods (`CurrentUser.requireManagement`, `checkAccountAccess`, author check in `correct`) and throw `ForbiddenException` → HTTP 403 with a clear message. The UI hides buttons, but the API never trusts the UI.

## Consequences

- Rules are next to the business logic and covered by service tests.
- A new rule is one `if` in the right method.
