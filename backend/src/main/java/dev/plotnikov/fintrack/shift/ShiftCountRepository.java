package dev.plotnikov.fintrack.shift;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftCountRepository extends JpaRepository<ShiftCount, Long> {
    List<ShiftCount> findByShiftId(Long shiftId);
}
