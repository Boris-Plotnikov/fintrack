package dev.plotnikov.fintrack.account;

import dev.plotnikov.fintrack.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class RateService {
    private final ExchangeRateRepository rates;

    public RateService(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    public BigDecimal rateToEur(String currency, LocalDate date) {
        if (Money.BASE_CURRENCY.equals(currency)) {
            return BigDecimal.ONE;
        }
        return rates.findFirstByCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(currency, date)
                .map(ExchangeRate::getRateToEur)
                .orElseThrow(() -> new IllegalStateException("No exchange rate for " + currency + " on " + date));
    }
}
