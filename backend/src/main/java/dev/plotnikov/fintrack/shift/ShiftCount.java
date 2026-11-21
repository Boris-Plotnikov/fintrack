package dev.plotnikov.fintrack.shift;

import dev.plotnikov.fintrack.account.Account;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "shift_counts")
public class ShiftCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shiftId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    private Account account;
    private BigDecimal expected;
    private BigDecimal counted;
    private BigDecimal difference;
    private boolean withinTolerance;
    private String comment;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public BigDecimal getExpected() { return expected; }
    public void setExpected(BigDecimal expected) { this.expected = expected; }
    public BigDecimal getCounted() { return counted; }
    public void setCounted(BigDecimal counted) { this.counted = counted; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public boolean isWithinTolerance() { return withinTolerance; }
    public void setWithinTolerance(boolean withinTolerance) { this.withinTolerance = withinTolerance; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
