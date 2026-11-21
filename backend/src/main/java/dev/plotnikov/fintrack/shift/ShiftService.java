package dev.plotnikov.fintrack.shift;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountRepository;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.AccountType;
import dev.plotnikov.fintrack.anomaly.AnomalyService;
import dev.plotnikov.fintrack.anomaly.AnomalyType;
import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.ForbiddenException;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.common.NotFoundException;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {
    public record CountInput(Long accountId, BigDecimal counted, String comment) {}

    public record AccountToCount(Long accountId, String name, String currency) {}

    public record OpenShiftView(Long shiftId, Instant openedAt, String office, List<AccountToCount> accountsToCount) {}

    public record CountResult(String account, String currency, BigDecimal expected, BigDecimal counted,
                              BigDecimal difference, boolean withinTolerance, String comment) {}

    public record CloseResult(Long shiftId, Instant closedAt, List<CountResult> counts, boolean hasDiscrepancy) {}

    public record ShiftRow(Long id, String user, String office, Instant openedAt, Instant closedAt, ShiftStatus status,
                           List<CountResult> counts) {}

    private final ShiftRepository shifts;
    private final ShiftCountRepository counts;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final AnomalyService anomalies;
    private final AuditService audit;
    private final Clock clock;

    public ShiftService(ShiftRepository shifts, ShiftCountRepository counts, AccountRepository accountRepository,
                        AccountService accountService, AnomalyService anomalies, AuditService audit, Clock clock) {
        this.shifts = shifts;
        this.counts = counts;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.anomalies = anomalies;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<OpenShiftView> current(AppUser user) {
        return shifts.findFirstByUserIdAndStatus(user.getId(), ShiftStatus.OPEN).map(this::view);
    }

    @Transactional
    public OpenShiftView open(AppUser user) {
        if (user.getOffice() == null) {
            throw new IllegalStateException("Shifts are opened in an office; the owner works without shifts");
        }
        if (shifts.findFirstByUserIdAndStatus(user.getId(), ShiftStatus.OPEN).isPresent()) {
            throw new IllegalStateException("You already have an open shift");
        }
        Shift s = new Shift();
        s.setUser(user);
        s.setOffice(user.getOffice());
        s.setOpenedAt(Instant.now(clock));
        s.setStatus(ShiftStatus.OPEN);
        shifts.save(s);
        audit.log(user, "SHIFT_OPENED", "shift", s.getId(), "Office " + user.getOffice().getName());
        return view(s);
    }

    @Transactional
    public CloseResult close(Long shiftId, List<CountInput> inputs, AppUser user) {
        Shift shift = shifts.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift " + shiftId + " not found"));
        if (!shift.getUser().getId().equals(user.getId()) && !CurrentUser.isManagement(user)) {
            throw new ForbiddenException("Only the person on the shift or a manager can close it");
        }
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new IllegalStateException("This shift is already closed");
        }
        List<Account> toCount = cashAccounts(shift);
        Map<Long, CountInput> byAccount = new HashMap<>();
        for (CountInput input : inputs) {
            byAccount.put(input.accountId(), input);
        }

        List<CountResult> results = new ArrayList<>();
        List<ShiftCount> rows = new ArrayList<>();
        for (Account account : toCount) {
            CountInput input = byAccount.get(account.getId());
            if (input == null || input.counted() == null) {
                throw new IllegalArgumentException("Enter the counted amount for " + account.getName());
            }
            if (input.counted().signum() < 0) {
                throw new IllegalArgumentException("Counted cash cannot be negative");
            }
            BigDecimal expected = accountService.balance(account);
            BigDecimal counted = Money.scale(input.counted());
            BigDecimal difference = counted.subtract(expected);
            boolean ok = difference.abs().compareTo(account.getTolerance()) <= 0;
            String comment = input.comment() == null || input.comment().isBlank() ? null : input.comment().trim();
            if (!ok && comment == null) {
                throw new IllegalArgumentException("The amount in " + account.getName()
                        + " differs from the records more than allowed. Count again or explain the difference in a comment.");
            }
            ShiftCount row = new ShiftCount();
            row.setShiftId(shift.getId());
            row.setAccount(account);
            row.setExpected(expected);
            row.setCounted(counted);
            row.setDifference(difference);
            row.setWithinTolerance(ok);
            row.setComment(comment);
            rows.add(row);
            results.add(new CountResult(account.getName(), account.getCurrency(), expected, counted, difference, ok, comment));
        }
        counts.saveAll(rows);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(Instant.now(clock));

        boolean discrepancy = false;
        for (ShiftCount row : rows) {
            if (!row.isWithinTolerance()) {
                discrepancy = true;
                anomalies.raise(AnomalyType.SHIFT_DISCREPANCY, "HIGH", "Shift of " + user.getFullName() + ": "
                        + row.getAccount().getName() + " differs by " + row.getDifference() + " "
                        + row.getAccount().getCurrency() + ". Comment: " + row.getComment(), null, shift.getId(),
                        row.getAccount().getId());
            }
        }
        audit.log(user, "SHIFT_CLOSED", "shift", shift.getId(), discrepancy ? "closed with discrepancy" : "closed, all counts within tolerance");
        return new CloseResult(shift.getId(), shift.getClosedAt(), results, discrepancy);
    }

    @Transactional(readOnly = true)
    public List<ShiftRow> history() {
        List<ShiftRow> rows = new ArrayList<>();
        for (Shift s : shifts.findTop30ByOrderByOpenedAtDesc()) {
            List<CountResult> c = counts.findByShiftId(s.getId()).stream()
                    .map(r -> new CountResult(r.getAccount().getName(), r.getAccount().getCurrency(), r.getExpected(),
                            r.getCounted(), r.getDifference(), r.isWithinTolerance(), r.getComment()))
                    .toList();
            rows.add(new ShiftRow(s.getId(), s.getUser().getFullName(), s.getOffice().getName(), s.getOpenedAt(),
                    s.getClosedAt(), s.getStatus(), c));
        }
        return rows;
    }

    private List<Account> cashAccounts(Shift shift) {
        return accountRepository.findByOfficeIdAndTypeAndActiveTrue(shift.getOffice().getId(), AccountType.CASH);
    }

    private OpenShiftView view(Shift s) {
        List<AccountToCount> list = cashAccounts(s).stream()
                .map(a -> new AccountToCount(a.getId(), a.getName(), a.getCurrency())).toList();
        return new OpenShiftView(s.getId(), s.getOpenedAt(), s.getOffice().getName(), list);
    }
}
