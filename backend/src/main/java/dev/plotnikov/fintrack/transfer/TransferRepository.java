package dev.plotnikov.fintrack.transfer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    List<Transfer> findAllByOrderByCreatedAtDesc();

    List<Transfer> findByStatus(TransferStatus status);
}
