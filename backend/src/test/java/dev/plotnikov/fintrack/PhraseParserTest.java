package dev.plotnikov.fintrack;

import static org.assertj.core.api.Assertions.assertThat;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountType;
import dev.plotnikov.fintrack.account.Category;
import dev.plotnikov.fintrack.account.CategoryKind;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.operation.OperationKind;
import dev.plotnikov.fintrack.operation.PhraseParser;
import dev.plotnikov.fintrack.user.Office;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhraseParserTest {

    private final PhraseParser parser = new PhraseParser();
    private final List<Account> accounts = List.of(
            account(1L, "Cash Vienna", AccountType.CASH, "EUR", "Vienna"),
            account(2L, "Bank Vienna", AccountType.BANK, "EUR", "Vienna"),
            account(3L, "Cash Prague", AccountType.CASH, "CZK", "Prague"));
    private final List<Category> categories = List.of(
            category(1L, "Travel", CategoryKind.EXPENSE), category(2L, "Other expenses", CategoryKind.EXPENSE),
            category(3L, "Consulting services", CategoryKind.INCOME), category(4L, "Other income", CategoryKind.INCOME));

    private static Account account(Long id, String name, AccountType type, String currency, String office) {
        Office o = new Office();
        o.setName(office);
        Account a = new Account();
        a.setId(id);
        a.setName(name);
        a.setType(type);
        a.setCurrency(currency);
        a.setOffice(o);
        a.setActive(true);
        return a;
    }

    private static Category category(Long id, String name, CategoryKind kind) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setKind(kind);
        return c;
    }

    @Test
    void expenseWithAccountAndCategory() {
        PhraseParser.Draft d = parser.parse("Paid 120 EUR for taxi from Cash Vienna", accounts, categories);
        assertThat(d.kind()).isEqualTo(OperationKind.EXPENSE);
        assertThat(d.amount()).isEqualByComparingTo("120.00");
        assertThat(d.accountName()).isEqualTo("Cash Vienna");
        assertThat(d.categoryName()).isEqualTo("Travel");
        assertThat(d.warnings()).isEmpty();
    }

    @Test
    void incomeWithCounterpartyAndEuropeanNumber() {
        PhraseParser.Draft d = parser.parse("Received 2.500,50 EUR from Donau Logistik GmbH for consulting to Bank Vienna", accounts, categories);
        assertThat(d.kind()).isEqualTo(OperationKind.INCOME);
        assertThat(d.amount()).isEqualByComparingTo("2500.50");
        assertThat(d.counterparty()).isEqualTo("Donau Logistik GmbH");
        assertThat(d.categoryName()).isEqualTo("Consulting services");
        assertThat(d.accountName()).isEqualTo("Bank Vienna");
    }

    @Test
    void accountGuessedFromOfficeAndTypeWords() {
        PhraseParser.Draft d = parser.parse("spent 300 on train, cash prague", accounts, categories);
        assertThat(d.accountName()).isEqualTo("Cash Prague");
        assertThat(d.currency()).isEqualTo("CZK");
    }

    @Test
    void warnsAboutWhatIsMissing() {
        PhraseParser.Draft d = parser.parse("something happened", accounts, categories);
        assertThat(d.amount()).isNull();
        assertThat(d.accountId()).isNull();
        assertThat(d.warnings()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void moneyRounding() {
        assertThat(Money.of("10.005")).isEqualByComparingTo("10.01");
        assertThat(Money.toEur(new BigDecimal("100.00"), new BigDecimal("0.039700"))).isEqualByComparingTo("3.97");
        assertThat(Money.convert(new BigDecimal("100.00"), BigDecimal.ONE, new BigDecimal("0.039700"))).isEqualByComparingTo("2518.89");
    }
}
