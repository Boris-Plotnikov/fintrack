package dev.plotnikov.fintrack.operation;

import java.time.LocalDate;

public record OperationFilter(
        LocalDate from,
        LocalDate to,
        Long accountId,
        Long officeId,
        Long categoryId,
        OperationKind kind,
        String search,
        Boolean includeCorrections) {}
