package dev.plotnikov.fintrack.account;

import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.common.NotFoundException;
import dev.plotnikov.fintrack.operation.OperationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final RateService rates;
    private final Clock clock;

    public AccountService(AccountRepository accounts, OperationRepository operations, RateService rates, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.rates = rates;
        this.clock = clock;
    }

    public Account find(Long id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Account " + id + " not found"));
    }

    public BigDecimal balance(Account account) {
        return Money.scale(account.getOpeningBalance().add(operations.sumByAccount(account.getId())));
    }

    @Transactional(readOnly = true)
    public List<AccountDto> list() {
        LocalDate today = LocalDate.now(clock);
        return accounts.findAllByOrderByOfficeIdAscNameAsc().stream().map(a -> {
            BigDecimal balance = balance(a);
            return new AccountDto(a.getId(), a.getName(), a.getType(), a.getCurrency(), a.getOffice().getName(),
                    a.getOwner().getFullName(), a.getManager().getFullName(), balance,
                    Money.toEur(balance, rates.rateToEur(a.getCurrency(), today)), a.getTolerance(), a.isActive());
        }).toList();
    }
}
