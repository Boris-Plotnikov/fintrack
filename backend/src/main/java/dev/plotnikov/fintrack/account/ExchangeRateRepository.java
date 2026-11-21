package dev.plotnikov.fintrack.account;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findFirstByCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(String currency, LocalDate date);

    List<ExchangeRate> findAllByOrderByValidFromDescCurrencyAsc();
}
