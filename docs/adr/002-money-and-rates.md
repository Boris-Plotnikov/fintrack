# ADR-002: BigDecimal, fixed rates, calculated balances

- **Status:** Accepted
- **Date:** 2026-11-18

## Decision

- Java `BigDecimal` with scale 2 and `HALF_UP` rounding, in one helper class `Money`. Database `NUMERIC(18,2)`; rates `NUMERIC(18,6)` as "1 unit = x EUR".
- Each operation stores amount, currency, rate and amount in EUR at the time of the operation.
- Balances are never stored: opening balance + `SUM(amount)`.

## Consequences

- No floating point errors; reports in EUR do not change when today's rate changes.
- Balance query per account; fine for thousands of operations, an index on `account_id` is enough.
