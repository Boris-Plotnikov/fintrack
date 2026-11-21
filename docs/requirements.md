# Requirements

## Business goal

The owner of a small multi-office service company wants to know at any moment: profit, turnover, how much money is where — and be told only about exceptions.

## Users and roles

| Role | Person in the demo | May |
|---|---|---|
| OWNER | Anna Berger | everything; owner deposits and withdrawals; works without shifts |
| MANAGER | Markus Huber | all accounts, confirms transfers, reviews anomalies, reports, audit, import |
| EMPLOYEE | Lena (Vienna), Julia (Munich), Tomas (Prague) | operations on accounts of their office, own corrections, transfers from their office, shifts |

## User stories

1. **Quick entry.** As an employee I want to type an operation like a chat message.
   - Given "Paid 120 EUR for taxi from Cash Vienna", then the draft shows expense, 120.00, Cash Vienna, Travel; nothing is saved until I confirm.
2. **Correct without deleting.** As an employee I want to fix a typo.
   - Given expense 50.00, when I correct it to 45.00 with a reason, then the original stays −50.00, a correction +5.00 is linked, the effective amount is −45.00, the status is "corrected".
   - When I correct it to 0, then the status is "cancelled".
3. **Late correction is visible.** As the owner I want to know about corrections made 24 hours or more later.
4. **Cash cannot go negative.** Given Cash Vienna has 100.00, when an expense of 120.00 is entered, then it is rejected with "Not enough cash".
5. **Transfer in transit.** As a manager I want to send cash to an office and the receiver to confirm it.
   - Given 300 EUR from Bank Vienna to Cash Vienna, then Bank Vienna is −300 at once, Cash Vienna changes only after Lena confirms; if rejected, Bank Vienna gets +300 back.
6. **Blind shift close.** As the owner I want employees to count cash without seeing the expected amount.
   - Given tolerance 5 EUR, when counted cash differs by 2 EUR, then the shift closes quietly; by 20 EUR, then a comment is required and an anomaly is created.
7. **Monthly report** with income and expenses by category and profit by office, CSV export.
8. **Bank statement import** with a check step and duplicate protection.

## Non-functional

- Money in `BigDecimal` with 2 decimals, `NUMERIC(18,2)` in the database.
- All amounts in reports in EUR at the stored rate.
- Every change in the audit log.
