package dev.plotnikov.fintrack.account;

import java.math.BigDecimal;

public record AccountDto(
        Long id,
        String name,
        AccountType type,
        String currency,
        String office,
        String owner,
        String manager,
        BigDecimal balance,
        BigDecimal balanceEur,
        BigDecimal tolerance,
        boolean active) {}
