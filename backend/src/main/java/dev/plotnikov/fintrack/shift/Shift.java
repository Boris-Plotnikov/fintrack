package dev.plotnikov.fintrack.shift;

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
import java.time.Instant;

@Entity
@Table(name = "shifts")
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "office_id")
    private Office office;
    private Instant openedAt;
    private Instant closedAt;
    @Enumerated(EnumType.STRING)
    private ShiftStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Office getOffice() { return office; }
    public void setOffice(Office office) { this.office = office; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public ShiftStatus getStatus() { return status; }
    public void setStatus(ShiftStatus status) { this.status = status; }
}
