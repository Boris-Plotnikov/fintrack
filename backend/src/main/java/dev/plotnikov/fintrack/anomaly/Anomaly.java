package dev.plotnikov.fintrack.anomaly;

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
import java.time.Instant;

@Entity
@Table(name = "anomalies")
public class Anomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AnomalyType type;
    private String severity;
    private String message;
    private Long operationId;
    private Long shiftId;
    private Long accountId;
    @Enumerated(EnumType.STRING)
    private AnomalyStatus status;
    private Instant createdAt;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;
    private String resolutionNote;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AnomalyType getType() { return type; }
    public void setType(AnomalyType type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getOperationId() { return operationId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public AnomalyStatus getStatus() { return status; }
    public void setStatus(AnomalyStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public AppUser getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(AppUser resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
}
