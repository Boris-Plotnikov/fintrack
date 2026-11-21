package dev.plotnikov.fintrack.demo;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountRepository;
import dev.plotnikov.fintrack.account.AccountType;
import dev.plotnikov.fintrack.account.Category;
import dev.plotnikov.fintrack.account.CategoryKind;
import dev.plotnikov.fintrack.account.CategoryRepository;
import dev.plotnikov.fintrack.account.ExchangeRate;
import dev.plotnikov.fintrack.account.ExchangeRateRepository;
import dev.plotnikov.fintrack.anomaly.Anomaly;
import dev.plotnikov.fintrack.anomaly.AnomalyRepository;
import dev.plotnikov.fintrack.anomaly.AnomalyStatus;
import dev.plotnikov.fintrack.anomaly.AnomalyType;
import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.config.FinTrackProperties;
import dev.plotnikov.fintrack.operation.Operation;
import dev.plotnikov.fintrack.operation.OperationKind;
import dev.plotnikov.fintrack.operation.OperationRepository;
import dev.plotnikov.fintrack.shift.Shift;
import dev.plotnikov.fintrack.shift.ShiftCount;
import dev.plotnikov.fintrack.shift.ShiftCountRepository;
import dev.plotnikov.fintrack.shift.ShiftRepository;
import dev.plotnikov.fintrack.shift.ShiftStatus;
import dev.plotnikov.fintrack.transfer.Transfer;
import dev.plotnikov.fintrack.transfer.TransferRepository;
import dev.plotnikov.fintrack.transfer.TransferStatus;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.AppUserRepository;
import dev.plotnikov.fintrack.user.Office;
import dev.plotnikov.fintrack.user.OfficeRepository;
import dev.plotnikov.fintrack.user.Role;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    static final int DAYS_BACK = 92;

    private final JdbcTemplate jdbc;
    private final OfficeRepository offices;
    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final ExchangeRateRepository rates;
    private final OperationRepository operations;
    private final TransferRepository transfers;
    private final ShiftRepository shifts;
    private final ShiftCountRepository shiftCounts;
    private final AnomalyRepository anomalies;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final FinTrackProperties properties;
    private final Clock clock;

    private Random random;
    private ZoneId zone;
    private final Map<String, Account> acc = new HashMap<>();
    private final Map<String, Category> cat = new HashMap<>();
    private final Map<String, AppUser> people = new HashMap<>();
    private final Map<Long, BigDecimal> balances = new HashMap<>();

    public DemoDataService(JdbcTemplate jdbc, OfficeRepository offices, AppUserRepository users,
                           AccountRepository accounts, CategoryRepository categories, ExchangeRateRepository rates,
                           OperationRepository operations, TransferRepository transfers, ShiftRepository shifts,
                           ShiftCountRepository shiftCounts, AnomalyRepository anomalies, AuditService audit,
                           PasswordEncoder encoder, FinTrackProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.offices = offices;
        this.users = users;
        this.accounts = accounts;
        this.categories = categories;
        this.rates = rates;
        this.operations = operations;
        this.transfers = transfers;
        this.shifts = shifts;
        this.shiftCounts = shiftCounts;
        this.anomalies = anomalies;
        this.audit = audit;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isEmpty() {
        return users.count() == 0;
    }

    @Transactional
    public void reset() {
        for (String table : List.of("audit_log", "anomalies", "shift_counts", "operations", "transfers", "shifts",
                "accounts", "exchange_rates", "categories", "users", "offices")) {
            jdbc.update("DELETE FROM " + table);
        }
        seed();
    }

    @Transactional
    public void seed() {
        random = new Random(2026);
        zone = ZoneId.of(properties.timezone());
        acc.clear();
        cat.clear();
        people.clear();
        balances.clear();

        Office vienna = office("VIE", "Vienna");
        Office munich = office("MUC", "Munich");
        Office prague = office("PRG", "Prague");

        String hash = encoder.encode(properties.demoPassword());
        person("anna", "Anna Berger", Role.OWNER, null, hash);
        person("markus", "Markus Huber", Role.MANAGER, vienna, hash);
        person("lena", "Lena Wagner", Role.EMPLOYEE, vienna, hash);
        person("julia", "Julia Schmid", Role.EMPLOYEE, munich, hash);
        person("tomas", "Tomas Novak", Role.EMPLOYEE, prague, hash);

        account("Bank Vienna", AccountType.BANK, "EUR", vienna, "anna", "markus", "48000", "0");
        account("Cash Vienna", AccountType.CASH, "EUR", vienna, "anna", "lena", "2500", "5");
        account("USD Account", AccountType.BANK, "USD", vienna, "anna", "anna", "12000", "0");
        account("Bank Munich", AccountType.BANK, "EUR", munich, "anna", "markus", "31000", "0");
        account("Cash Munich", AccountType.CASH, "EUR", munich, "anna", "julia", "1800", "5");
        account("CHF Account", AccountType.BANK, "CHF", munich, "anna", "markus", "8000", "0");
        account("Bank Prague", AccountType.BANK, "CZK", prague, "anna", "markus", "640000", "0");
        account("Cash Prague", AccountType.CASH, "CZK", prague, "anna", "tomas", "45000", "100");

        for (String name : List.of("Consulting services", "Training services", "Support contracts", "Other income")) {
            category(name, CategoryKind.INCOME);
        }
        for (String name : List.of("Salaries", "Rent", "Travel", "Software", "Office supplies", "Marketing",
                "Client entertainment", "Bank fees", "Taxes", "Other expenses")) {
            category(name, CategoryKind.EXPENSE);
        }

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = today.minusDays(DAYS_BACK);
        seedRates(start, today);
        seedOperations(start, today);
        seedShiftsAndAnomalies(today);
        audit.log(people.get("anna"), "DEMO_DATA_CREATED", "system", null, "Demo data of Alpine Services GmbH created");
        log.info("Demo data created: {} operations", operations.count());
    }

    private Office office(String code, String name) {
        Office o = new Office();
        o.setCode(code);
        o.setName(name);
        return offices.save(o);
    }

    private void person(String username, String fullName, Role role, Office office, String hash) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setRole(role);
        u.setOffice(office);
        u.setPasswordHash(hash);
        people.put(username, users.save(u));
    }

    private void account(String name, AccountType type, String currency, Office office, String owner, String manager,
                         String opening, String tolerance) {
        Account a = new Account();
        a.setName(name);
        a.setType(type);
        a.setCurrency(currency);
        a.setOffice(office);
        a.setOwner(people.get(owner));
        a.setManager(people.get(manager));
        a.setOpeningBalance(Money.of(opening));
        a.setTolerance(Money.of(tolerance));
        a.setActive(true);
        accounts.save(a);
        acc.put(name, a);
        balances.put(a.getId(), a.getOpeningBalance());
    }

    private void category(String name, CategoryKind kind) {
        Category c = new Category();
        c.setName(name);
        c.setKind(kind);
        cat.put(name, categories.save(c));
    }

    private void seedRates(LocalDate start, LocalDate today) {
        Map<String, Double> rate = new HashMap<>(Map.of("USD", 0.925, "CHF", 1.062, "CZK", 0.0397));
        for (LocalDate d = start.minusDays(7); !d.isAfter(today); d = d.plusDays(7)) {
            for (Map.Entry<String, Double> e : rate.entrySet()) {
                double next = e.getValue() * (1 + (random.nextDouble() - 0.5) * 0.012);
                e.setValue(next);
                ExchangeRate r = new ExchangeRate();
                r.setCurrency(e.getKey());
                r.setRateToEur(BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP));
                r.setValidFrom(d);
                rates.save(r);
            }
        }
    }

    private void seedOperations(LocalDate start, LocalDate today) {
        String[] clients = {"Donau Logistik GmbH", "Isar Medical AG", "Vltava Software s.r.o.", "Bergbahn Tirol GmbH",
                "Nordlicht Versicherung AG", "Praha Retail a.s.", "Kaffeehaus Kette GmbH", "Alpen Energie AG"};
        String[][] cashExpenses = {
                {"Taxi to client meeting", "Travel"}, {"Coffee and milk for the office", "Office supplies"},
                {"Printer paper and toner", "Office supplies"}, {"Lunch with client", "Client entertainment"},
                {"Train tickets", "Travel"}, {"Parking", "Travel"}, {"Flowers for reception", "Office supplies"}};

        boolean ownerDepositDone = false;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            int dom = day.getDayOfMonth();

            if (dom == 1) {
                op(OperationKind.EXPENSE, "Bank Vienna", "Rent", "-4200", "Office rent " + day.getMonth(), "Ringstrasse Immobilien GmbH", day, "markus");
                op(OperationKind.EXPENSE, "Bank Munich", "Rent", "-3600", "Office rent " + day.getMonth(), "Maxvorstadt Offices KG", day, "markus");
                op(OperationKind.EXPENSE, "Bank Prague", "Rent", "-88000", "Office rent " + day.getMonth(), "Karlin Business Park a.s.", day, "markus");
            }
            if (dom == 5) {
                op(OperationKind.EXPENSE, "Bank Vienna", "Software", "-" + amount(380, 520), "Cloud and software subscriptions", "Various vendors", day, "markus");
                op(OperationKind.EXPENSE, "Bank Vienna", "Bank fees", "-" + amount(18, 35), "Account fees", "Erste Testbank", day, "markus");
                op(OperationKind.EXPENSE, "Bank Munich", "Bank fees", "-" + amount(15, 28), "Account fees", "Testbank Bayern", day, "markus");
            }
            if (dom == 15) {
                op(OperationKind.EXPENSE, "Bank Vienna", "Taxes", "-" + amount(2600, 3400), "VAT advance payment", "Finanzamt (demo)", day, "anna");
            }
            if (dom == 28) {
                op(OperationKind.EXPENSE, "Bank Vienna", "Salaries", "-18400", "Salaries " + day.getMonth(), "Payroll", day, "anna");
                op(OperationKind.EXPENSE, "Bank Munich", "Salaries", "-14200", "Salaries " + day.getMonth(), "Payroll", day, "anna");
                op(OperationKind.EXPENSE, "Bank Prague", "Salaries", "-410000", "Salaries " + day.getMonth(), "Payroll", day, "anna");
            }
            if (!ownerDepositDone && day.isAfter(start.plusDays(20))) {
                op(OperationKind.OWNER_DEPOSIT, "Bank Vienna", null, "15000", "Owner contribution for expansion to Prague", "Anna Berger", day, "anna");
                ownerDepositDone = true;
            }
            if (weekend) {
                continue;
            }

            String[][] bankIncome = {{"Bank Vienna", "markus"}, {"Bank Munich", "markus"}, {"Bank Prague", "markus"}};
            for (String[] bank : bankIncome) {
                if (random.nextDouble() < 0.45) {
                    boolean prague = bank[0].equals("Bank Prague");
                    boolean training = random.nextDouble() < 0.35;
                    String value = prague ? amount(18000, 120000) : amount(800, training ? 3200 : 6800);
                    String client = clients[random.nextInt(clients.length)];
                    op(OperationKind.INCOME, bank[0], training ? "Training services" : "Consulting services", value,
                            (training ? "Workshop" : "Consulting") + " invoice " + (1000 + random.nextInt(9000)), client, day, bank[1]);
                }
            }
            if (dom == 10) {
                op(OperationKind.INCOME, "USD Account", "Support contracts", amount(2400, 3600), "Monthly support, US client", "Brightline Inc. (demo)", day, "anna");
                op(OperationKind.INCOME, "CHF Account", "Support contracts", amount(1800, 2600), "Monthly support, Swiss client", "Zuger Daten AG", day, "markus");
            }
            if (random.nextDouble() < 0.12) {
                op(OperationKind.EXPENSE, "Bank Vienna", "Marketing", "-" + amount(150, 900), "Online ads", "Ad platform", day, "markus");
            }

            cashDay("Cash Vienna", "lena", cashExpenses, day, false);
            cashDay("Cash Munich", "julia", cashExpenses, day, false);
            cashDay("Cash Prague", "tomas", cashExpenses, day, true);
            if (random.nextDouble() < 0.18) {
                op(OperationKind.INCOME, "Cash Vienna", "Training services", amount(120, 450), "Seminar fee paid in cash", "Walk-in participant", day, "lena");
            }

            refill("Bank Vienna", "Cash Vienna", "400", "1500", day);
            refill("Bank Munich", "Cash Munich", "400", "1200", day);
            refill("Bank Prague", "Cash Prague", "8000", "30000", day);
        }

        Transfer pending = transfer("Bank Munich", "Cash Munich", "800", today, "markus", TransferStatus.PENDING);
        pending.setComment("Cash for the client workshop on Friday");
        transfers.save(pending);

        List<Operation> recent = operations.findByAccountIdOrderByOperationAtDesc(acc.get("Cash Vienna").getId());
        Operation typo = recent.stream().filter(o -> o.getKind() == OperationKind.EXPENSE).skip(2).findFirst().orElse(null);
        if (typo != null) {
            correction(typo, typo.getAmount().multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_UP),
                    "Typo: receipt shows a smaller amount", "lena", Duration.ofHours(2));
        }
        List<Operation> bank = operations.findByAccountIdOrderByOperationAtDesc(acc.get("Bank Vienna").getId());
        Operation late = bank.stream().filter(o -> o.getKind() == OperationKind.INCOME).skip(6).findFirst().orElse(null);
        if (late != null) {
            Operation c = correction(late, late.getAmount().subtract(new BigDecimal("650.00")),
                    "Client got a discount after the invoice was sent", "markus", Duration.ofDays(3));
            anomaly(AnomalyType.LATE_CORRECTION, "MEDIUM", "Operation #" + late.getId()
                    + " corrected 72 h after it was entered by Markus Huber: client got a discount", late.getId(), null,
                    late.getAccount().getId(), AnomalyStatus.NEW, c.getCreatedAt());
            anomaly(AnomalyType.LARGE_CORRECTION, "HIGH", "Correction of operation #" + late.getId()
                    + " changes it by -650.00 EUR (client discount)", late.getId(), null, late.getAccount().getId(),
                    AnomalyStatus.REVIEWED, c.getCreatedAt());
        }
    }

    private void cashDay(String account, String user, String[][] options, LocalDate day, boolean czk) {
        int count = random.nextInt(3);
        for (int i = 0; i < count; i++) {
            String[] pick = options[random.nextInt(options.length)];
            String value = czk ? amount(150, 1400) : amount(6, 65);
            op(OperationKind.EXPENSE, account, pick[1], "-" + value, pick[0], null, day, user);
        }
    }

    private void refill(String from, String to, String below, String amount, LocalDate day) {
        if (balances.get(acc.get(to).getId()).compareTo(new BigDecimal(below)) < 0) {
            transfer(from, to, amount, day, "markus", TransferStatus.CONFIRMED);
        }
    }

    private Transfer transfer(String from, String to, String value, LocalDate day, String user, TransferStatus status) {
        Instant at = at(day, 9);
        Transfer t = new Transfer();
        t.setFromAccount(acc.get(from));
        t.setToAccount(acc.get(to));
        t.setAmount(Money.of(value));
        t.setAmountReceived(Money.of(value));
        t.setStatus(status);
        t.setComment("Cash refill");
        t.setCreatedBy(people.get(user));
        t.setCreatedAt(at);
        if (status == TransferStatus.CONFIRMED) {
            t.setDecidedBy(acc.get(to).getManager());
            t.setDecidedAt(at.plus(Duration.ofHours(2)));
        }
        transfers.save(t);
        Operation out = raw(OperationKind.TRANSFER_OUT, acc.get(from), null, Money.of(value).negate(),
                "Transfer #" + t.getId() + " to " + to, null, at, people.get(user));
        out.setTransferId(t.getId());
        operations.save(out);
        if (status == TransferStatus.CONFIRMED) {
            Operation in = raw(OperationKind.TRANSFER_IN, acc.get(to), null, Money.of(value),
                    "Transfer #" + t.getId() + " from " + from, null, t.getDecidedAt(), acc.get(to).getManager());
            in.setTransferId(t.getId());
            operations.save(in);
        }
        return t;
    }

    private void op(OperationKind kind, String account, String category, String value, String description,
                    String counterparty, LocalDate day, String user) {
        Account a = acc.get(account);
        BigDecimal amount = Money.of(value);
        if (a.getType() == AccountType.CASH && balances.get(a.getId()).add(amount).signum() < 0) {
            return;
        }
        int hour = 8 + random.nextInt(10);
        operations.save(raw(kind, a, category == null ? null : cat.get(category), amount, description, counterparty,
                at(day, hour), people.get(user)));
    }

    private Operation raw(OperationKind kind, Account a, Category c, BigDecimal amount, String description,
                          String counterparty, Instant at, AppUser user) {
        Operation o = new Operation();
        o.setKind(kind);
        o.setAccount(a);
        o.setCategory(c);
        o.setAmount(amount);
        o.setCurrency(a.getCurrency());
        o.setRateToEur(rateOn(a.getCurrency(), at));
        o.setAmountEur(Money.toEur(amount, o.getRateToEur()));
        o.setDescription(description);
        o.setCounterparty(counterparty);
        o.setOperationAt(at);
        o.setCreatedBy(user);
        o.setCreatedAt(at.plus(Duration.ofMinutes(random.nextInt(40))));
        o.setSource("SEED");
        balances.merge(a.getId(), amount, BigDecimal::add);
        return o;
    }

    private Operation correction(Operation original, BigDecimal newSigned, String reason, String user, Duration after) {
        BigDecimal delta = newSigned.subtract(original.getAmount());
        Instant at = original.getCreatedAt().plus(after);
        if (at.isAfter(Instant.now(clock))) {
            at = Instant.now(clock).minus(Duration.ofMinutes(30));
        }
        Operation c = raw(OperationKind.CORRECTION, original.getAccount(), original.getCategory(), delta, reason,
                original.getCounterparty(), at, people.get(user));
        c.setRateToEur(original.getRateToEur());
        c.setAmountEur(Money.toEur(delta, original.getRateToEur()));
        c.setCreatedAt(at);
        c.setCorrectsId(original.getId());
        return operations.save(c);
    }

    private void seedShiftsAndAnomalies(LocalDate today) {
        String[][] staff = {{"lena", "Cash Vienna"}, {"julia", "Cash Munich"}, {"tomas", "Cash Prague"}};
        for (int back = 10; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            if (day.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            for (String[] s : staff) {
                AppUser u = people.get(s[0]);
                Account cash = acc.get(s[1]);
                Shift shift = new Shift();
                shift.setUser(u);
                shift.setOffice(u.getOffice());
                shift.setOpenedAt(at(day, 8));
                shift.setClosedAt(at(day, 18));
                shift.setStatus(ShiftStatus.CLOSED);
                shifts.save(shift);

                BigDecimal expected = balances.get(cash.getId());
                BigDecimal difference = BigDecimal.ZERO;
                String comment = null;
                if (back == 4 && s[0].equals("tomas")) {
                    difference = new BigDecimal("-500.00");
                    comment = "Paid the courier in cash, receipt is missing";
                } else if (random.nextDouble() < 0.3) {
                    difference = new BigDecimal(cash.getCurrency().equals("CZK") ? "-40.00" : "-1.20");
                }
                ShiftCount count = new ShiftCount();
                count.setShiftId(shift.getId());
                count.setAccount(cash);
                count.setExpected(expected);
                count.setCounted(expected.add(difference));
                count.setDifference(difference);
                count.setWithinTolerance(difference.abs().compareTo(cash.getTolerance()) <= 0);
                count.setComment(comment);
                shiftCounts.save(count);
                if (!count.isWithinTolerance()) {
                    anomaly(AnomalyType.SHIFT_DISCREPANCY, "HIGH", "Shift of " + u.getFullName() + ": " + cash.getName()
                            + " differs by " + difference + " " + cash.getCurrency() + ". Comment: " + comment, null,
                            shift.getId(), cash.getId(), AnomalyStatus.RESOLVED, shift.getClosedAt());
                }
            }
        }
        List<Operation> big = operations.findAll().stream()
                .filter(o -> o.getKind() == OperationKind.INCOME && o.getAmountEur().compareTo(properties.largeAmountEur()) > 0)
                .limit(2).toList();
        for (Operation o : big) {
            anomaly(AnomalyType.LARGE_AMOUNT, "MEDIUM", "Large income of " + o.getAmount() + " " + o.getCurrency()
                    + " on " + o.getAccount().getName() + ": " + o.getDescription(), o.getId(), null,
                    o.getAccount().getId(), AnomalyStatus.NEW, o.getCreatedAt());
        }
    }

    private void anomaly(AnomalyType type, String severity, String message, Long operationId, Long shiftId,
                         Long accountId, AnomalyStatus status, Instant at) {
        Anomaly a = new Anomaly();
        a.setType(type);
        a.setSeverity(severity);
        a.setMessage(message);
        a.setOperationId(operationId);
        a.setShiftId(shiftId);
        a.setAccountId(accountId);
        a.setStatus(status);
        a.setCreatedAt(at);
        if (status != AnomalyStatus.NEW) {
            a.setResolvedBy(people.get("anna"));
            a.setResolutionNote(status == AnomalyStatus.RESOLVED ? "Receipt found and booked as courier expense" : "Discount agreed with the client, checked");
        }
        anomalies.save(a);
    }

    private Instant at(LocalDate day, int hour) {
        Instant t = day.atTime(hour, random.nextInt(60)).atZone(zone).toInstant();
        Instant now = Instant.now(clock);
        return t.isAfter(now) ? now.minus(Duration.ofMinutes(5 + random.nextInt(60))) : t;
    }

    private String amount(int min, int max) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal rateOn(String currency, Instant at) {
        if ("EUR".equals(currency)) {
            return BigDecimal.ONE;
        }
        return rates.findFirstByCurrencyAndValidFromLessThanEqualOrderByValidFromDesc(currency, at.atZone(zone).toLocalDate())
                .map(ExchangeRate::getRateToEur).orElseThrow();
    }
}
