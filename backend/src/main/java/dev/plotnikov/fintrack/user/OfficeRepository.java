package dev.plotnikov.fintrack.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeRepository extends JpaRepository<Office, Long> {
    Optional<Office> findByCode(String code);
}
