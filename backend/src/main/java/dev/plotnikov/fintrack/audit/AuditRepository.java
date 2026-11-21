package dev.plotnikov.fintrack.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findTop15ByOrderByAtDesc();

    Page<AuditEntry> findAllByOrderByAtDesc(Pageable pageable);
}
