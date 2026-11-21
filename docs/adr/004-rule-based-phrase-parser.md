# ADR-004: Rule-based phrase parser

- **Status:** Accepted
- **Date:** 2026-11-18

## Context

The real system lets employees write operations in free text; an AI model turns them into records. For this project the phrases are short and predictable.

## Decision

`PhraseParser` with regular expressions and word lists: income/expense words, amount with European and English number formats, currency, the longest account name found in the text (or office + "cash"/"bank"), category keywords, counterparty after "from"/"to". It returns a draft with warnings; the user always confirms.

## Consequences

- Free, instant, fully testable.
- Unusual phrases give warnings instead of wrong data. An AI parser (like in JobRadar) can be added behind the same `Draft` result.
