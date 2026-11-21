package dev.plotnikov.fintrack.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OperationDto(
        Long id,
        OperationKind kind,
        Long accountId,
        String account,
        String office,
        String category,
        BigDecimal amount,
        BigDecimal effectiveAmount,
        String currency,
        BigDecimal rateToEur,
        BigDecimal effectiveAmountEur,
        String description,
        String counterparty,
        Instant operationAt,
        String createdBy,
        Instant createdAt,
        String source,
        Long correctsId,
        Long transferId,
        String status,
        List<Correction> corrections) {
    public record Correction(Long id, BigDecimal delta, String reason, String by, Instant at) {}
}
