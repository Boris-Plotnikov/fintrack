package dev.plotnikov.fintrack.transfer;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.AccountService;
import dev.plotnikov.fintrack.account.RateService;
import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.ForbiddenException;
import dev.plotnikov.fintrack.common.Money;
import dev.plotnikov.fintrack.common.NotFoundException;
import dev.plotnikov.fintrack.config.FinTrackProperties;
import dev.plotnikov.fintrack.operation.OperationKind;
import dev.plotnikov.fintrack.operation.OperationService;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final TransferRepository transfers;
    private final AccountService accounts;
    private final OperationService operations;
    private final RateService rates;
    private final AuditService audit;
    private final FinTrackProperties properties;
    private final Clock clock;

    public TransferService(TransferRepository transfers, AccountService accounts, OperationService operations,
                           RateService rates, AuditService audit, FinTrackProperties properties, Clock clock) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.operations = operations;
        this.rates = rates;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TransferDto create(Long fromId, Long toId, BigDecimal amount, String comment, AppUser user) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Choose two different accounts");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        Account from = accounts.find(fromId);
        Account to = accounts.find(toId);
        operations.checkAccountAccess(from, user);
        if (!to.isActive()) {
            throw new IllegalArgumentException("Account " + to.getName() + " is closed");
        }
        BigDecimal sent = Money.scale(amount);
        operations.checkCashNotNegative(from, sent.negate());

        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(properties.timezone())));
        BigDecimal received = from.getCurrency().equals(to.getCurrency()) ? sent
                : Money.convert(sent, rates.rateToEur(from.getCurrency(), today), rates.rateToEur(to.getCurrency(), today));

        Instant now = Instant.now(clock);
        Transfer t = new Transfer();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(sent);
        t.setAmountReceived(received);
        t.setStatus(TransferStatus.PENDING);
        t.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        t.setCreatedBy(user);
        t.setCreatedAt(now);
        transfers.save(t);

        operations.createSystem(OperationKind.TRANSFER_OUT, from, null, sent.negate(),
                "Transfer #" + t.getId() + " to " + to.getName(), null, now, user, t.getId(), "SYSTEM");
        audit.log(user, "TRANSFER_CREATED", "transfer", t.getId(),
                sent + " " + from.getCurrency() + " from " + from.getName() + " to " + to.getName());
        return TransferDto.from(t);
    }

    @Transactional
    public TransferDto confirm(Long id, AppUser user) {
        Transfer t = pending(id);
        checkCanDecide(t, user);
        Instant now = Instant.now(clock);
        operations.createSystem(OperationKind.TRANSFER_IN, t.getToAccount(), null, t.getAmountReceived(),
                "Transfer #" + t.getId() + " from " + t.getFromAccount().getName(), null, now, user, t.getId(), "SYSTEM");
        t.setStatus(TransferStatus.CONFIRMED);
        t.setDecidedBy(user);
        t.setDecidedAt(now);
        audit.log(user, "TRANSFER_CONFIRMED", "transfer", id, t.getAmountReceived() + " " + t.getToAccount().getCurrency()
                + " arrived on " + t.getToAccount().getName());
        return TransferDto.from(t);
    }

    @Transactional
    public TransferDto reject(Long id, String reason, AppUser user) {
        Transfer t = pending(id);
        checkCanDecide(t, user);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Write why the transfer is rejected");
        }
        Instant now = Instant.now(clock);
        operations.createSystem(OperationKind.TRANSFER_IN, t.getFromAccount(), null, t.getAmount(),
                "Transfer #" + t.getId() + " rejected, money returned: " + reason.trim(), null, now, user, t.getId(), "SYSTEM");
        t.setStatus(TransferStatus.REJECTED);
        t.setDecidedBy(user);
        t.setDecidedAt(now);
        t.setComment((t.getComment() == null ? "" : t.getComment() + " | ") + "Rejected: " + reason.trim());
        audit.log(user, "TRANSFER_REJECTED", "transfer", id, reason.trim());
        return TransferDto.from(t);
    }

    @Transactional(readOnly = true)
    public List<TransferDto> list() {
        return transfers.findAllByOrderByCreatedAtDesc().stream().map(TransferDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal inTransitEur() {
        LocalDate today = LocalDate.now(clock);
        BigDecimal sum = BigDecimal.ZERO;
        for (Transfer t : transfers.findByStatus(TransferStatus.PENDING)) {
            sum = sum.add(Money.toEur(t.getAmount(), rates.rateToEur(t.getFromAccount().getCurrency(), today)));
        }
        return Money.scale(sum);
    }

    private Transfer pending(Long id) {
        Transfer t = transfers.findById(id).orElseThrow(() -> new NotFoundException("Transfer " + id + " not found"));
        if (t.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer #" + id + " is already " + t.getStatus().name().toLowerCase());
        }
        return t;
    }

    private static void checkCanDecide(Transfer t, AppUser user) {
        boolean receiver = t.getToAccount().getManager().getId().equals(user.getId());
        if (!receiver && !CurrentUser.isManagement(user)) {
            throw new ForbiddenException("Only " + t.getToAccount().getManager().getFullName() + " or a manager can confirm this transfer");
        }
    }
}
