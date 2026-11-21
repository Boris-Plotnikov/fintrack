package dev.plotnikov.fintrack.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final String BASE_CURRENCY = "EUR";

    private Money() {}

    public static BigDecimal of(String value) {
        return scale(new BigDecimal(value));
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal toEur(BigDecimal amount, BigDecimal rateToEur) {
        return scale(amount.multiply(rateToEur));
    }

    public static BigDecimal convert(BigDecimal amount, BigDecimal fromRate, BigDecimal toRate) {
        return amount.multiply(fromRate).divide(toRate, 2, RoundingMode.HALF_UP);
    }
}
