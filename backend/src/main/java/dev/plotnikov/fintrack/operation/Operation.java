package dev.plotnikov.fintrack.operation;

import dev.plotnikov.fintrack.account.Account;
import dev.plotnikov.fintrack.account.Category;
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
@Table(name = "operations")
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OperationKind kind;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;
    private BigDecimal amount;
    private String currency;
    private BigDecimal rateToEur;
    private BigDecimal amountEur;
    private String description;
    private String counterparty;
    private Instant operationAt;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;
    private Instant createdAt;
    private Long correctsId;
    private Long transferId;
    private Long shiftId;
    private String source;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OperationKind getKind() { return kind; }
    public void setKind(OperationKind kind) { this.kind = kind; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getRateToEur() { return rateToEur; }
    public void setRateToEur(BigDecimal rateToEur) { this.rateToEur = rateToEur; }
    public BigDecimal getAmountEur() { return amountEur; }
    public void setAmountEur(BigDecimal amountEur) { this.amountEur = amountEur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }
    public Instant getOperationAt() { return operationAt; }
    public void setOperationAt(Instant operationAt) { this.operationAt = operationAt; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCorrectsId() { return correctsId; }
    public void setCorrectsId(Long correctsId) { this.correctsId = correctsId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
