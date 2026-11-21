package dev.plotnikov.fintrack.operation;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.AccountType;
import dev.plotnikov.fintrack.account.Category;
import dev.plotnikov.fintrack.account.CategoryKind;
import dev.plotnikov.fintrack.account.CategoryRepository;
import dev.plotnikov.fintrack.account.RateService;
import dev.plotnikov.fintrack.anomaly.AnomalyService;
import dev.plotnikov.fintrack.anomaly.AnomalyType;
import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.ForbiddenException;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.common.NotFoundException;
import dev.plotnikov.fintrack.config.FinTrackProperties;
import dev.plotnikov.fintrack.shift.ShiftRepository;
import dev.plotnikov.fintrack.shift.ShiftStatus;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.CurrentUser;
import dev.plotnikov.fintrack.user.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationService {
    static final Set<OperationKind> USER_KINDS = Set.of(OperationKind.INCOME, OperationKind.EXPENSE,
            OperationKind.OWNER_DEPOSIT, OperationKind.OWNER_WITHDRAWAL);
    static final Duration MAX_BACKDATE = Duration.ofDays(31);

    private final OperationRepository operations;
    private final AccountService accounts;
    private final CategoryRepository categories;
    private final RateService rates;
    private final AnomalyService anomalies;
    private final AuditService audit;
    private final ShiftRepository shifts;
    private final FinTrackProperties properties;
    private final Clock clock;

    public OperationService(OperationRepository operations, AccountService accounts, CategoryRepository categories,
                            RateService rates, AnomalyService anomalies, AuditService audit, ShiftRepository shifts,
                            FinTrackProperties properties, Clock clock) {
        this.operations = operations;
        this.accounts = accounts;
        this.categories = categories;
        this.rates = rates;
        this.anomalies = anomalies;
        this.audit = audit;
        this.shifts = shifts;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OperationDto create(OperationRequests.Create request, AppUser user) {
        if (!USER_KINDS.contains(request.kind())) {
            throw new IllegalArgumentException(request.kind() + " is created by the system, not by hand");
        }
        Account account = accounts.find(request.accountId());
        checkAccountAccess(account, user);
        if ((request.kind() == OperationKind.OWNER_DEPOSIT || request.kind() == OperationKind.OWNER_WITHDRAWAL)
                && user.getRole() != Role.OWNER) {
            throw new ForbiddenException("Only the owner records deposits and withdrawals of the owner");
        }

        Category category = null;
        if (request.kind() == OperationKind.INCOME || request.kind() == OperationKind.EXPENSE) {
            if (request.categoryId() == null) {
                throw new IllegalArgumentException("Choose a category");
            }
            category = categories.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            CategoryKind expected = request.kind() == OperationKind.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
            if (category.getKind() != expected) {
                throw new IllegalArgumentException("Category '" + category.getName() + "' is not an " + expected.name().toLowerCase() + " category");
            }
        }

        Instant now = Instant.now(clock);
        Instant at = request.operationAt() == null ? now : request.operationAt();
        if (at.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("An operation cannot be in the future");
        }
        if (at.isBefore(now.minus(MAX_BACKDATE))) {
            throw new IllegalArgumentException("An operation older than 31 days must be entered by the accountant");
        }

        BigDecimal signed = signedAmount(request.kind(), Money.scale(request.amount()));
        checkCashNotNegative(account, signed);

        Operation op = new Operation();
        op.setKind(request.kind());
        op.setAccount(account);
        op.setCategory(category);
        op.setAmount(signed);
        op.setCurrency(account.getCurrency());
        op.setRateToEur(rates.rateToEur(account.getCurrency(), at.atZone(zone()).toLocalDate()));
        op.setAmountEur(Money.toEur(signed, op.getRateToEur()));
        op.setDescription(request.description().trim());
        op.setCounterparty(blankToNull(request.counterparty()));
        op.setOperationAt(at);
        op.setCreatedBy(user);
        op.setCreatedAt(now);
        op.setSource(request.source() == null ? "FORM" : request.source());
        shifts.findFirstByUserIdAndStatus(user.getId(), ShiftStatus.OPEN).ifPresent(s -> op.setShiftId(s.getId()));
        operations.save(op);

        afterChange(op, account);
        if (op.getAmountEur().abs().compareTo(properties.largeAmountEur()) > 0) {
            anomalies.raise(AnomalyType.LARGE_AMOUNT, "MEDIUM", "Large " + op.getKind().name().toLowerCase() + " of "
                    + op.getAmount().abs() + " " + op.getCurrency() + " on " + account.getName() + ": "
                    + op.getDescription(), op.getId(), op.getShiftId(), account.getId());
        }
        audit.log(user, "OPERATION_CREATED", "operation", op.getId(),
                op.getKind() + " " + op.getAmount() + " " + op.getCurrency() + " on " + account.getName() + " — " + op.getDescription());
        return toDto(op, List.of());
    }

    @Transactional
    public Operation createSystem(OperationKind kind, Account account, Category category, BigDecimal signedAmount,
                                  String description, String counterparty, Instant at, AppUser user, Long transferId,
                                  String source) {
        Operation op = new Operation();
        op.setKind(kind);
        op.setAccount(account);
        op.setCategory(category);
        op.setAmount(Money.scale(signedAmount));
        op.setCurrency(account.getCurrency());
        op.setRateToEur(rates.rateToEur(account.getCurrency(), at.atZone(zone()).toLocalDate()));
        op.setAmountEur(Money.toEur(op.getAmount(), op.getRateToEur()));
        op.setDescription(description);
        op.setCounterparty(counterparty);
        op.setOperationAt(at);
        op.setCreatedBy(user);
        op.setCreatedAt(Instant.now(clock));
        op.setTransferId(transferId);
        op.setSource(source);
        return operations.save(op);
    }

    @Transactional
    public OperationDto correct(Long id, OperationRequests.Correct request, AppUser user) {
        Operation original = operations.findById(id).orElseThrow(() -> new NotFoundException("Operation " + id + " not found"));
        if (!USER_KINDS.contains(original.getKind())) {
            throw new IllegalArgumentException("Corrections and transfers cannot be corrected; correct the original operation or reject the transfer");
        }
        boolean isAuthor = original.getCreatedBy().getId().equals(user.getId());
        if (!isAuthor && !CurrentUser.isManagement(user)) {
            throw new ForbiddenException("Only the author or a manager can correct this operation");
        }

        List<Operation> previous = operations.findByCorrectsIdOrderByCreatedAt(id);
        BigDecimal effective = effectiveAmount(original, previous);
        BigDecimal newSigned = original.getAmount().signum() >= 0
                ? Money.scale(request.newAmount()) : Money.scale(request.newAmount()).negate();
        BigDecimal delta = newSigned.subtract(effective);
        if (delta.signum() == 0) {
            throw new IllegalArgumentException("The new amount is the same as the current one");
        }
        Account account = original.getAccount();
        checkCashNotNegative(account, delta);

        Instant now = Instant.now(clock);
        Operation correction = new Operation();
        correction.setKind(OperationKind.CORRECTION);
        correction.setAccount(account);
        correction.setCategory(original.getCategory());
        correction.setAmount(delta);
        correction.setCurrency(original.getCurrency());
        correction.setRateToEur(original.getRateToEur());
        correction.setAmountEur(Money.toEur(delta, original.getRateToEur()));
        correction.setDescription(request.reason().trim());
        correction.setCounterparty(original.getCounterparty());
        correction.setOperationAt(now);
        correction.setCreatedBy(user);
        correction.setCreatedAt(now);
        correction.setCorrectsId(original.getId());
        correction.setSource("FORM");
        operations.save(correction);

        afterChange(correction, account);
        Duration age = Duration.between(original.getCreatedAt(), now);
        if (age.toHours() >= properties.lateCorrectionHours()) {
            anomalies.raise(AnomalyType.LATE_CORRECTION, "MEDIUM", "Operation #" + id + " corrected " + age.toHours()
                    + " h after it was entered by " + user.getFullName() + ": " + request.reason(), id, null, account.getId());
        }
        if (correction.getAmountEur().abs().compareTo(properties.largeCorrectionEur()) > 0) {
            anomalies.raise(AnomalyType.LARGE_CORRECTION, "HIGH", "Correction of operation #" + id + " changes it by "
                    + delta + " " + correction.getCurrency() + " (" + request.reason() + ")", id, null, account.getId());
        }
        audit.log(user, newSigned.signum() == 0 ? "OPERATION_CANCELLED" : "OPERATION_CORRECTED", "operation", id,
                "was " + effective + ", now " + newSigned + " " + original.getCurrency() + ". Reason: " + request.reason());

        List<Operation> all = new ArrayList<>(previous);
        all.add(correction);
        return toDto(original, all);
    }

    @Transactional(readOnly = true)
    public Page<OperationDto> list(OperationFilter filter, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Order.desc("operationAt"), Sort.Order.desc("id")));
        Page<Operation> result = operations.findAll(OperationSpecifications.fromFilter(filter, zone()), request);
        List<Long> ids = result.getContent().stream().map(Operation::getId).toList();
        Map<Long, List<Operation>> corrections = ids.isEmpty() ? Map.of()
                : operations.findByCorrectsIdIn(ids).stream().collect(Collectors.groupingBy(Operation::getCorrectsId));
        return result.map(op -> toDto(op, corrections.getOrDefault(op.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public OperationDto get(Long id) {
        Operation op = operations.findById(id).orElseThrow(() -> new NotFoundException("Operation " + id + " not found"));
        return toDto(op, operations.findByCorrectsIdOrderByCreatedAt(id));
    }

    static BigDecimal effectiveAmount(Operation original, List<Operation> corrections) {
        BigDecimal sum = original.getAmount();
        for (Operation c : corrections) {
            sum = sum.add(c.getAmount());
        }
        return Money.scale(sum);
    }

    static BigDecimal signedAmount(OperationKind kind, BigDecimal positive) {
        return switch (kind) {
            case INCOME, OWNER_DEPOSIT, TRANSFER_IN -> positive;
            case EXPENSE, OWNER_WITHDRAWAL, TRANSFER_OUT -> positive.negate();
            case CORRECTION -> throw new IllegalArgumentException("A correction has no fixed sign");
        };
    }

    public void checkAccountAccess(Account account, AppUser user) {
        if (!account.isActive()) {
            throw new IllegalArgumentException("Account " + account.getName() + " is closed");
        }
        if (user.getRole() == Role.EMPLOYEE
                && (user.getOffice() == null || !user.getOffice().getId().equals(account.getOffice().getId()))) {
            throw new ForbiddenException("You can only use accounts of your office (" + (user.getOffice() == null ? "none" : user.getOffice().getName()) + ")");
        }
    }

    public void checkCashNotNegative(Account account, BigDecimal change) {
        if (account.getType() == AccountType.CASH && change.signum() < 0) {
            BigDecimal after = accounts.balance(account).add(change);
            if (after.signum() < 0) {
                throw new IllegalArgumentException("Not enough cash in " + account.getName() + ": balance "
                        + accounts.balance(account) + " " + account.getCurrency());
            }
        }
    }

    private void afterChange(Operation op, Account account) {
        if (account.getType() == AccountType.BANK && op.getAmount().signum() < 0) {
            BigDecimal balance = accounts.balance(account);
            if (balance.signum() < 0) {
                anomalies.raise(AnomalyType.NEGATIVE_BALANCE, "HIGH", account.getName() + " is in overdraft: "
                        + balance + " " + account.getCurrency(), op.getId(), op.getShiftId(), account.getId());
            }
        }
    }

    private ZoneId zone() {
        return ZoneId.of(properties.timezone());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    OperationDto toDto(Operation op, List<Operation> corrections) {
        BigDecimal effective = effectiveAmount(op, corrections);
        String status = corrections.isEmpty() ? "ACTIVE" : effective.signum() == 0 ? "CANCELLED" : "CORRECTED";
        return new OperationDto(op.getId(), op.getKind(), op.getAccount().getId(), op.getAccount().getName(),
                op.getAccount().getOffice().getName(), op.getCategory() == null ? null : op.getCategory().getName(),
                op.getAmount(), effective, op.getCurrency(), op.getRateToEur(), Money.toEur(effective, op.getRateToEur()),
                op.getDescription(), op.getCounterparty(), op.getOperationAt(), op.getCreatedBy().getFullName(),
                op.getCreatedAt(), op.getSource(), op.getCorrectsId(), op.getTransferId(), status,
                corrections.stream().map(c -> new OperationDto.Correction(c.getId(), c.getAmount(), c.getDescription(),
                        c.getCreatedBy().getFullName(), c.getCreatedAt())).toList());
    }
}
