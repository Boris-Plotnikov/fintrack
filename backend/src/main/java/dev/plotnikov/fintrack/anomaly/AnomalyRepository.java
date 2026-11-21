package dev.plotnikov.fintrack.anomaly;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    List<Anomaly> findAllByOrderByCreatedAtDesc();

    List<Anomaly> findByStatusOrderByCreatedAtDesc(AnomalyStatus status);

    long countByStatusNot(AnomalyStatus status);
}
