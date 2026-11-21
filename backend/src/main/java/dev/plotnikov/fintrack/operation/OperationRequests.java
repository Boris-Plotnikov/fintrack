package dev.plotnikov.fintrack.operation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class OperationRequests {
    private OperationRequests() {}

    public record Create(
            @NotNull OperationKind kind,
            @NotNull Long accountId,
            Long categoryId,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 300) String description,
            @Size(max = 120) String counterparty,
            Instant operationAt,
            String source) {}

    public record Correct(
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2) BigDecimal newAmount,
            @NotBlank @Size(max = 200) String reason) {}

    public record Phrase(@NotBlank @Size(max = 300) String text) {}
}
