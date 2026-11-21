package dev.plotnikov.fintrack.audit;

import dev.plotnikov.fintrack.user.AppUser;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void log(AppUser user, String action, String entity, Long entityId, String details) {
        AuditEntry e = new AuditEntry();
        e.setAt(Instant.now(clock));
        e.setUserId(user == null ? null : user.getId());
        e.setUsername(user == null ? "system" : user.getUsername());
        e.setAction(action);
        e.setEntity(entity);
        e.setEntityId(entityId);
        e.setDetails(details.length() > 1000 ? details.substring(0, 1000) : details);
        repository.save(e);
    }
}
