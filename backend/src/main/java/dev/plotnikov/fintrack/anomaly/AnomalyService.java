package dev.plotnikov.fintrack.anomaly;

import dev.plotnikov.fintrack.audit.AuditService;
import dev.plotnikov.fintrack.common.NotFoundException;
import dev.plotnikov.fintrack.user.AppUser;
import dev.plotnikov.fintrack.user.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyService {
    private final AnomalyRepository anomalies;
    private final AuditService audit;
    private final Clock clock;

    public AnomalyService(AnomalyRepository anomalies, AuditService audit, Clock clock) {
        this.anomalies = anomalies;
        this.audit = audit;
        this.clock = clock;
    }

    public Anomaly raise(AnomalyType type, String severity, String message, Long operationId, Long shiftId,
                         Long accountId) {
        Anomaly a = new Anomaly();
        a.setType(type);
        a.setSeverity(severity);
        a.setMessage(message.length() > 400 ? message.substring(0, 400) : message);
        a.setOperationId(operationId);
        a.setShiftId(shiftId);
        a.setAccountId(accountId);
        a.setStatus(AnomalyStatus.NEW);
        a.setCreatedAt(Instant.now(clock));
        return anomalies.save(a);
    }

    @Transactional(readOnly = true)
    public List<Anomaly> list(AnomalyStatus status) {
        return status == null ? anomalies.findAllByOrderByCreatedAtDesc() : anomalies.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public Anomaly decide(Long id, AnomalyStatus status, String note, AppUser user) {
        CurrentUser.requireManagement(user, "review anomalies");
        if (status == AnomalyStatus.NEW) {
            throw new IllegalArgumentException("Choose REVIEWED or RESOLVED");
        }
        if (status == AnomalyStatus.RESOLVED && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Write a short note how it was resolved");
        }
        Anomaly a = anomalies.findById(id).orElseThrow(() -> new NotFoundException("Anomaly " + id + " not found"));
        a.setStatus(status);
        a.setResolvedBy(user);
        a.setResolutionNote(note);
        audit.log(user, "ANOMALY_" + status, "anomaly", id, a.getType() + ": " + (note == null ? "" : note));
        return a;
    }
}
