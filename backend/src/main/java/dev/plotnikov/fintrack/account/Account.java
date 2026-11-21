package dev.plotnikov.fintrack.account;

import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.Office;
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

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Enumerated(EnumType.STRING)
    private AccountType type;
    private String currency;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "office_id")
    private Office office;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private AppUser owner;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id")
    private AppUser manager;
    private BigDecimal openingBalance;
    private BigDecimal tolerance;
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Office getOffice() { return office; }
    public void setOffice(Office office) { this.office = office; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public AppUser getManager() { return manager; }
    public void setManager(AppUser manager) { this.manager = manager; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public BigDecimal getTolerance() { return tolerance; }
    public void setTolerance(BigDecimal tolerance) { this.tolerance = tolerance; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
