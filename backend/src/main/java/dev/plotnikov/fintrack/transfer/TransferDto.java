package dev.plotnikov.fintrack.transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferDto(
        Long id,
        Long fromAccountId,
        String fromAccount,
        String fromCurrency,
        Long toAccountId,
        String toAccount,
        String toCurrency,
        String toManager,
        Long toManagerId,
        BigDecimal amount,
        BigDecimal amountReceived,
        TransferStatus status,
        String comment,
        String createdBy,
        Instant createdAt,
        String decidedBy,
        Instant decidedAt) {

    public static TransferDto from(Transfer t) {
        return new TransferDto(t.getId(), t.getFromAccount().getId(), t.getFromAccount().getName(),
                t.getFromAccount().getCurrency(), t.getToAccount().getId(), t.getToAccount().getName(),
                t.getToAccount().getCurrency(), t.getToAccount().getManager().getFullName(),
                t.getToAccount().getManager().getId(), t.getAmount(), t.getAmountReceived(), t.getStatus(),
                t.getComment(), t.getCreatedBy().getFullName(), t.getCreatedAt(),
                t.getDecidedBy() == null ? null : t.getDecidedBy().getFullName(), t.getDecidedAt());
    }
}
