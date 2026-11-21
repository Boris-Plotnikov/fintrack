package dev.plotnikov.fintrack.report;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.AccountType;
import dev.plotnikov.fintrack.account.Category;
import dev.plotnikov.fintrack.account.CategoryKind;
import dev.plotnikov.fintrack.account.CategoryRepository;
import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.config.FinTrackProperties;
import dev.plotnikov.fintrack.operation.Operation;
import dev.plotnikov.fintrack.operation.OperationKind;
import dev.plotnikov.fintrack.operation.OperationRepository;
import dev.plotnikov.fintrack.operation.OperationService;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvService {
    public record ImportRow(int line, String date, BigDecimal amount, String description, String counterparty,
                            String category, String status, String error) {}

    public record ImportResult(int imported, int duplicates, int errors, boolean dryRun, List<ImportRow> rows) {}

    private final OperationRepository operations;
    private final OperationService operationService;
    private final AccountService accounts;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final ZoneId zone;

    public CsvService(OperationRepository operations, OperationService operationService, AccountService accounts,
                      CategoryRepository categories, AuditService audit, FinTrackProperties properties) {
        this.operations = operations;
        this.operationService = operationService;
        this.accounts = accounts;
        this.categories = categories;
        this.audit = audit;
        this.zone = ZoneId.of(properties.timezone());
    }

    @Transactional(readOnly = true)
    public String export(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        StringBuilder csv = new StringBuilder("id;date;kind;account;office;category;amount;currency;rate_to_eur;amount_eur;description;counterparty;corrects_id;created_by\n");
        for (Operation o : operations.findByOperationAtBetween(from, to)) {
            csv.append(o.getId()).append(';')
                    .append(o.getOperationAt().atZone(zone).toLocalDateTime()).append(';')
                    .append(o.getKind()).append(';')
                    .append(clean(o.getAccount().getName())).append(';')
                    .append(clean(o.getAccount().getOffice().getName())).append(';')
                    .append(o.getCategory() == null ? "" : clean(o.getCategory().getName())).append(';')
                    .append(o.getAmount()).append(';')
                    .append(o.getCurrency()).append(';')
                    .append(o.getRateToEur()).append(';')
                    .append(o.getAmountEur()).append(';')
                    .append(clean(o.getDescription())).append(';')
                    .append(o.getCounterparty() == null ? "" : clean(o.getCounterparty())).append(';')
                    .append(o.getCorrectsId() == null ? "" : o.getCorrectsId()).append(';')
                    .append(clean(o.getCreatedBy().getFullName())).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public ImportResult importStatement(Long accountId, String content, boolean dryRun, AppUser user) {
        CurrentUser.requireManagement(user, "import bank statements");
        Account account = accounts.find(accountId);
        if (account.getType() != AccountType.BANK) {
            throw new IllegalArgumentException("Statements can only be imported into a bank account");
        }
        Category otherIncome = categories.findByName("Other income").orElseThrow();
        Category otherExpense = categories.findByName("Other expenses").orElseThrow();
        List<Category> all = categories.findAll();

        List<ImportRow> rows = new ArrayList<>();
        int imported = 0;
        int duplicates = 0;
        int errors = 0;
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && line.toLowerCase(Locale.ROOT).startsWith("date"))) {
                continue;
            }
            String[] parts = line.split(";", -1);
            if (parts.length < 3) {
                rows.add(new ImportRow(i + 1, null, null, line, null, null, "ERROR", "Expected date;amount;description;counterparty"));
                errors++;
                continue;
            }
            LocalDate date;
            BigDecimal amount;
            try {
                date = LocalDate.parse(parts[0].trim());
                amount = Money.scale(new BigDecimal(parts[1].trim().replace(",", ".")));
            } catch (DateTimeParseException | NumberFormatException e) {
                rows.add(new ImportRow(i + 1, parts[0], null, parts[2], null, null, "ERROR", "Bad date (YYYY-MM-DD) or amount"));
                errors++;
                continue;
            }
            if (amount.signum() == 0) {
                rows.add(new ImportRow(i + 1, date.toString(), amount, parts[2], null, null, "ERROR", "Amount is zero"));
                errors++;
                continue;
            }
            String description = parts[2].trim().isEmpty() ? "Bank statement line" : parts[2].trim();
            String counterparty = parts.length > 3 && !parts[3].isBlank() ? parts[3].trim() : null;
            Category category = guessCategory(description, amount.signum() > 0, all, otherIncome, otherExpense);
            Instant at = date.atTime(12, 0).atZone(zone).toInstant();

            if (operations.existsByAccountIdAndOperationAtAndAmountAndDescription(account.getId(), at, amount, description)) {
                rows.add(new ImportRow(i + 1, date.toString(), amount, description, counterparty, category.getName(), "DUPLICATE", "Already imported"));
                duplicates++;
                continue;
            }
            if (!dryRun) {
                operationService.createSystem(amount.signum() > 0 ? OperationKind.INCOME : OperationKind.EXPENSE, account,
                        category, amount, description, counterparty, at, user, null, "IMPORT");
            }
            rows.add(new ImportRow(i + 1, date.toString(), amount, description, counterparty, category.getName(), dryRun ? "READY" : "IMPORTED", null));
            imported++;
        }
        if (!dryRun && imported > 0) {
            audit.log(user, "STATEMENT_IMPORTED", "account", account.getId(), imported + " lines imported into " + account.getName());
        }
        return new ImportResult(imported, duplicates, errors, dryRun, rows);
    }

    static Category guessCategory(String description, boolean income, List<Category> all, Category otherIncome,
                                  Category otherExpense) {
        String text = description.toLowerCase(Locale.ROOT);
        for (Category c : all) {
            boolean kindMatches = (c.getKind() == CategoryKind.INCOME) == income;
            String firstWord = c.getName().toLowerCase(Locale.ROOT).split(" ")[0];
            if (kindMatches && firstWord.length() > 3 && text.contains(firstWord)) {
                return c;
            }
        }
        return income ? otherIncome : otherExpense;
    }

    private static String clean(String s) {
        return s.replace(";", ",").replace("\n", " ");
    }
}
