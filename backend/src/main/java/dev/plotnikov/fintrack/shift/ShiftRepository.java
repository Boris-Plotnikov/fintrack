package dev.plotnikov.fintrack.shift;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findFirstByUserIdAndStatus(Long userId, ShiftStatus status);

    List<Shift> findTop30ByOrderByOpenedAtDesc();
}
