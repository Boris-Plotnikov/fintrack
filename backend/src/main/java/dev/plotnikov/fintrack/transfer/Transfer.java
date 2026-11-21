package dev.plotnikov.fintrack.transfer;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;
    private BigDecimal amount;
    private BigDecimal amountReceived;
    @Enumerated(EnumType.STRING)
    private TransferStatus status;
    private String comment;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;
    private Instant createdAt;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;
    private Instant decidedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Account getFromAccount() { return fromAccount; }
    public void setFromAccount(Account fromAccount) { this.fromAccount = fromAccount; }
    public Account getToAccount() { return toAccount; }
    public void setToAccount(Account toAccount) { this.toAccount = toAccount; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getAmountReceived() { return amountReceived; }
    public void setAmountReceived(BigDecimal amountReceived) { this.amountReceived = amountReceived; }
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public AppUser getDecidedBy() { return decidedBy; }
    public void setDecidedBy(AppUser decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
