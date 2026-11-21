package dev.plotnikov.fintrack.account;

import dev.plotnikov.fintrack.user.AppUserRepository;
import dev.plotnikov.fintrack.user.OfficeRepository;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReferenceController {
    private final AccountService accounts;
    private final CategoryRepository categories;
    private final OfficeRepository offices;
    private final ExchangeRateRepository rates;
    private final AppUserRepository users;

    public ReferenceController(AccountService accounts, CategoryRepository categories, OfficeRepository offices,
                               ExchangeRateRepository rates, AppUserRepository users) {
        this.accounts = accounts;
        this.categories = categories;
        this.offices = offices;
        this.rates = rates;
        this.users = users;
    }

    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        return accounts.list();
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return categories.findAllByOrderByKindAscNameAsc();
    }

    @GetMapping("/offices")
    public List<Map<String, Object>> offices() {
        return offices.findAll().stream().map(o -> Map.<String, Object>of("id", o.getId(), "code", o.getCode(), "name", o.getName())).toList();
    }

    @GetMapping("/rates")
    public List<ExchangeRate> rates() {
        return rates.findAllByOrderByValidFromDescCurrencyAsc();
    }

    @GetMapping("/people")
    public List<Map<String, Object>> people() {
        return users.findAllByOrderByRoleAscFullNameAsc().stream().map(u -> Map.<String, Object>of(
                "id", u.getId(), "fullName", u.getFullName(), "role", u.getRole(),
                "office", u.getOffice() == null ? "All offices" : u.getOffice().getName())).toList();
    }
}
