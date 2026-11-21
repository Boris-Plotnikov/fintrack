package dev.plotnikov.fintrack.operation;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.Category;
import dev.plotnikov.fintrack.account.CategoryKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PhraseParser {
    public record Draft(
            OperationKind kind,
            BigDecimal amount,
            String currency,
            Long accountId,
            String accountName,
            Long categoryId,
            String categoryName,
            String counterparty,
            String description,
            List<String> warnings) {}

    private static final Pattern AMOUNT = Pattern.compile(
            "(?:(EUR|USD|CHF|CZK|€|\\$)\\s*)?(\\d{1,3}(?:[ ,.]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*(EUR|USD|CHF|CZK|€|\\$|euro|euros)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTERPARTY = Pattern.compile(
            "\\b(?:from|to|by|client|supplier)\\s+([A-Z][\\w&.-]*(?:\\s+(?:[A-Z][\\w&.-]*|GmbH|AG|s\\.r\\.o\\.|Ltd))*)");

    private static final List<String> EXPENSE_WORDS = List.of("paid", "pay", "spent", "bought", "purchase", "expense", "fee", "rent", "salary", "bill");
    private static final List<String> INCOME_WORDS = List.of("received", "receive", "got", "income", "sold", "invoice paid", "payment from", "revenue");

    private static final Map<String, String> CATEGORY_WORDS = new LinkedHashMap<>();

    static {
        CATEGORY_WORDS.put("taxi", "Travel");
        CATEGORY_WORDS.put("train", "Travel");
        CATEGORY_WORDS.put("flight", "Travel");
        CATEGORY_WORDS.put("hotel", "Travel");
        CATEGORY_WORDS.put("fuel", "Travel");
        CATEGORY_WORDS.put("rent", "Rent");
        CATEGORY_WORDS.put("salary", "Salaries");
        CATEGORY_WORDS.put("bonus", "Salaries");
        CATEGORY_WORDS.put("license", "Software");
        CATEGORY_WORDS.put("software", "Software");
        CATEGORY_WORDS.put("subscription", "Software");
        CATEGORY_WORDS.put("paper", "Office supplies");
        CATEGORY_WORDS.put("coffee", "Office supplies");
        CATEGORY_WORDS.put("office", "Office supplies");
        CATEGORY_WORDS.put("ads", "Marketing");
        CATEGORY_WORDS.put("marketing", "Marketing");
        CATEGORY_WORDS.put("lunch", "Client entertainment");
        CATEGORY_WORDS.put("dinner", "Client entertainment");
        CATEGORY_WORDS.put("bank fee", "Bank fees");
        CATEGORY_WORDS.put("fee", "Bank fees");
        CATEGORY_WORDS.put("consulting", "Consulting services");
        CATEGORY_WORDS.put("training", "Training services");
        CATEGORY_WORDS.put("workshop", "Training services");
        CATEGORY_WORDS.put("support", "Support contracts");
        CATEGORY_WORDS.put("maintenance", "Support contracts");
    }

    public Draft parse(String text, List<Account> accounts, List<Category> categories) {
        List<String> warnings = new ArrayList<>();
        String lower = " " + text.toLowerCase(Locale.ROOT) + " ";

        OperationKind kind = null;
        if (INCOME_WORDS.stream().anyMatch(lower::contains)) {
            kind = OperationKind.INCOME;
        } else if (EXPENSE_WORDS.stream().anyMatch(lower::contains)) {
            kind = OperationKind.EXPENSE;
        } else {
            warnings.add("Could not tell income from expense — expense assumed");
            kind = OperationKind.EXPENSE;
        }

        Account account = null;
        for (Account a : accounts) {
            if (a.isActive() && lower.contains(a.getName().toLowerCase(Locale.ROOT))
                    && (account == null || a.getName().length() > account.getName().length())) {
                account = a;
            }
        }
        if (account == null) {
            account = guessAccountByWords(lower, accounts);
        }
        if (account == null) {
            warnings.add("No account found in the text — choose one");
        }

        String forAmount = account == null ? text : text.replaceAll("(?i)" + Pattern.quote(account.getName()), " ");
        BigDecimal amount = null;
        String currency = null;
        Matcher m = AMOUNT.matcher(forAmount);
        while (m.find()) {
            if (m.group(2) == null) {
                continue;
            }
            amount = parseNumber(m.group(2));
            currency = normalizeCurrency(m.group(1) != null ? m.group(1) : m.group(3));
            break;
        }
        if (amount == null) {
            warnings.add("No amount found");
        }
        if (account != null) {
            if (currency != null && !currency.equals(account.getCurrency())) {
                warnings.add("Text says " + currency + " but " + account.getName() + " is in " + account.getCurrency()
                        + " — the amount is booked in " + account.getCurrency());
            }
            currency = account.getCurrency();
        }

        Category category = null;
        CategoryKind wanted = kind == OperationKind.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        for (Map.Entry<String, String> e : CATEGORY_WORDS.entrySet()) {
            if (lower.contains(e.getKey())) {
                category = categories.stream()
                        .filter(c -> c.getName().equals(e.getValue()) && c.getKind() == wanted)
                        .findFirst().orElse(null);
                if (category != null) {
                    break;
                }
            }
        }
        if (category == null) {
            category = categories.stream()
                    .filter(c -> c.getKind() == wanted && c.getName().startsWith("Other"))
                    .findFirst().orElse(null);
            warnings.add("Category not recognised — 'Other' suggested");
        }

        String counterparty = null;
        Matcher cm = COUNTERPARTY.matcher(text);
        while (cm.find()) {
            String candidate = cm.group(1).trim();
            boolean isAccount = account != null && account.getName().toLowerCase(Locale.ROOT)
                    .contains(candidate.toLowerCase(Locale.ROOT));
            if (!isAccount) {
                counterparty = candidate;
                break;
            }
        }

        String description = text.trim();
        if (description.length() > 300) {
            description = description.substring(0, 300);
        }
        return new Draft(kind, amount, currency, account == null ? null : account.getId(),
                account == null ? null : account.getName(), category == null ? null : category.getId(),
                category == null ? null : category.getName(), counterparty, description, warnings);
    }

    private static Account guessAccountByWords(String lower, List<Account> accounts) {
        Account best = null;
        int bestHits = 0;
        for (Account a : accounts) {
            if (!a.isActive()) {
                continue;
            }
            int hits = 0;
            if (lower.contains(a.getOffice().getName().toLowerCase(Locale.ROOT))) {
                hits++;
            }
            if (lower.contains(" " + a.getType().name().toLowerCase(Locale.ROOT) + " ")) {
                hits++;
            }
            if (hits >= 2 && hits > bestHits) {
                best = a;
                bestHits = hits;
            }
        }
        return best;
    }

    static BigDecimal parseNumber(String raw) {
        String s = raw.replace(" ", "");
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int decimalPos = Math.max(lastDot, lastComma);
        boolean hasDecimals = decimalPos >= 0 && s.length() - decimalPos - 1 <= 2;
        if (hasDecimals) {
            String whole = s.substring(0, decimalPos).replace(".", "").replace(",", "");
            return new BigDecimal(whole + "." + s.substring(decimalPos + 1)).setScale(2);
        }
        return new BigDecimal(s.replace(".", "").replace(",", "")).setScale(2);
    }

    static String normalizeCurrency(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.toUpperCase(Locale.ROOT);
        if (c.equals("€") || c.startsWith("EURO")) {
            return "EUR";
        }
        if (c.equals("$")) {
            return "USD";
        }
        return c;
    }
}
