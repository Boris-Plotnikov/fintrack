# ADR-001: Corrections instead of updates and deletes

- **Status:** Accepted
- **Date:** 2026-11-18

## Context

In the real system the client asked: "if someone changes an amount, I want to see what it was, who changed it and when". In accounting, history must not be rewritten.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| UPDATE the row + audit table | Simple queries | The audit table is the only truth about the past; easy to forget |
| Soft delete + new row | History kept | Two rows look like two operations in reports |
| Append a linked CORRECTION with the difference | History is the data itself; balance = simple SUM | Effective amount = original + corrections |

## Decision

An operation is never updated or deleted. A correction is a new row with `kind = CORRECTION`, `corrects_id` = original, the amount = difference, the same category and rate. Cancelling = correcting to 0.

## Consequences

- `SUM(amount)` per account is always the correct balance — no special cases.
- A correction counts in the month it is made, so closed months never change.
- The UI groups corrections under their original.
