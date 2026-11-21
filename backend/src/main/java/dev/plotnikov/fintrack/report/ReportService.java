package dev.plotnikov.fintrack.report;

import dev.plotnikov.fintrack.account.AccountDto;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.CategoryKind;
import dev.plotnikov.fintrack.anomaly.AnomalyRepository;
import dev.plotnikov.fintrack.anomaly.AnomalyStatus;
import dev.plotnikov.fintrack.audit.AuditEntry;
import dev.plotnikov.fintrack.audit.AuditRepository;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.config.FinTrackProperties;
import dev.plotnikov.fintrack.operation.Operation;
import dev.plotnikov.fintrack.operation.OperationRepository;
import dev.plotnikov.fintrack.transfer.TransferRepository;
import dev.plotnikov.fintrack.transfer.TransferService;
import dev.plotnikov.fintrack.transfer.TransferStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    public record Line(String name, BigDecimal amountEur) {}

    public record Day(String date, BigDecimal income, BigDecimal expense) {}

    public record Monthly(String month, BigDecimal income, BigDecimal expense, BigDecimal profit, BigDecimal turnover,
                          List<Line> incomeByCategory, List<Line> expenseByCategory, List<Line> profitByOffice,
                          List<Day> daily, long operations) {}

    public record Dashboard(Monthly currentMonth, Monthly previousMonth, BigDecimal totalBalanceEur,
                            BigDecimal inTransitEur, long pendingTransfers, long openAnomalies,
                            List<AccountDto> accounts, List<AuditEntry> activity) {}

    private final OperationRepository operations;
    private final AccountService accounts;
    private final TransferService transferService;
    private final TransferRepository transfers;
    private final AnomalyRepository anomalies;
    private final AuditRepository auditRepository;
    private final ZoneId zone;
    private final Clock clock;

    public ReportService(OperationRepository operations, AccountService accounts, TransferService transferService,
                         TransferRepository transfers, AnomalyRepository anomalies, AuditRepository auditRepository,
                         FinTrackProperties properties, Clock clock) {
        this.operations = operations;
        this.accounts = accounts;
        this.transferService = transferService;
        this.transfers = transfers;
        this.anomalies = anomalies;
        this.auditRepository = auditRepository;
        this.zone = ZoneId.of(properties.timezone());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Monthly monthly(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return build(month.toString(), operations.findByOperationAtBetween(from, to), month);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        YearMonth now = YearMonth.now(clock.withZone(zone));
        List<AccountDto> list = accounts.list();
        BigDecimal total = list.stream().map(AccountDto::balanceEur).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard(monthly(now), monthly(now.minusMonths(1)), Money.scale(total),
                transferService.inTransitEur(), transfers.findByStatus(TransferStatus.PENDING).size(),
                anomalies.countByStatusNot(AnomalyStatus.RESOLVED), list, auditRepository.findTop15ByOrderByAtDesc());
    }

    static boolean isProfitRelevant(Operation o) {
        return o.getCategory() != null;
    }

    Monthly build(String label, List<Operation> ops, YearMonth month) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeByCategory = new TreeMap<>();
        Map<String, BigDecimal> expenseByCategory = new TreeMap<>();
        Map<String, BigDecimal> profitByOffice = new TreeMap<>();
        Map<LocalDate, BigDecimal[]> daily = new TreeMap<>();
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            daily.put(d, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Operation o : ops) {
            if (!isProfitRelevant(o)) {
                continue;
            }
            BigDecimal eur = o.getAmountEur();
            String category = o.getCategory().getName();
            LocalDate day = o.getOperationAt().atZone(zone).toLocalDate();
            BigDecimal[] dayValues = daily.get(day);
            if (o.getCategory().getKind() == CategoryKind.INCOME) {
                income = income.add(eur);
                incomeByCategory.merge(category, eur, BigDecimal::add);
                if (dayValues != null) {
                    dayValues[0] = dayValues[0].add(eur);
                }
            } else {
                expense = expense.add(eur.negate());
                expenseByCategory.merge(category, eur.negate(), BigDecimal::add);
                if (dayValues != null) {
                    dayValues[1] = dayValues[1].add(eur.negate());
                }
            }
            profitByOffice.merge(o.getAccount().getOffice().getName(), eur, BigDecimal::add);
        }

        List<Day> days = new ArrayList<>();
        daily.forEach((d, v) -> days.add(new Day(d.toString(), Money.scale(v[0]), Money.scale(v[1]))));
        return new Monthly(label, Money.scale(income), Money.scale(expense), Money.scale(income.subtract(expense)),
                Money.scale(income.add(expense)), sorted(incomeByCategory), sorted(expenseByCategory),
                sorted(profitByOffice), days, ops.size());
    }

    private static List<Line> sorted(Map<String, BigDecimal> map) {
        return map.entrySet().stream()
                .map(e -> new Line(e.getKey(), Money.scale(e.getValue())))
                .sorted((a, b) -> b.amountEur().compareTo(a.amountEur()))
                .toList();
    }
}
